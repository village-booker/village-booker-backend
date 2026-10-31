package com.villagebooker.service.booking;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.dto.booking.AccommodationBookingRequest;
import com.villagebooker.dto.booking.TourBookingRequest;
import com.villagebooker.dto.listing.UnavailablePeriodRequest;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.user.Role;
import com.villagebooker.model.Listing;
import com.villagebooker.model.User;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import com.villagebooker.service.listing.UnavailablePeriodService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Parallel requests for the same nights / places: exactly one may win (spec 5.0, 5.1).
// Not @Transactional: the threads must see each other's commits, so the data is committed and removed in @AfterEach.
@SpringBootTest
@AutoConfigureMockMvc
class BookingConcurrencyTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ListingRepo listingRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UnavailablePeriodService unavailablePeriodService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private ListingTestSupport support;
    private String owner;
    private final List<Long> listingIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        owner = support.ownerToken();
    }

    @AfterEach
    void cleanUp() {
        for (Long id : listingIds) {
            jdbc.update("delete from booking where listing_id = ?", id);
            jdbc.update("delete from unavailable_period where listing_id = ?", id);
            jdbc.update("delete from tour_date where tour_id = ?", id);
            jdbc.update("delete from accommodation where listing_id = ?", id);
            jdbc.update("delete from tour where listing_id = ?", id);
            Long ownerId = jdbc.queryForObject("select owner_id from listing where id = ?", Long.class, id);
            jdbc.update("delete from listing where id = ?", id);
            userIds.add(ownerId);
        }
        userIds.stream().distinct().forEach(id -> jdbc.update("delete from app_user where id = ?", id));
    }

    @Test
    void twoGuestsBookTheSameNightsAndOnlyOneWins() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        LocalDate d = AppTime.today(clock).plusDays(10);
        AccommodationBookingRequest request = new AccommodationBookingRequest(listingId, d, d.plusDays(2), 2, null);
        Long first = guest();
        Long second = guest();

        List<Boolean> results = race(
                () -> bookingService.createAccommodation(first, request),
                () -> bookingService.createAccommodation(second, request));

        assertThat(results).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void bookingAndUnavailablePeriodForTheSameNightCannotBothPass() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        Long ownerId = listingRepo.findById(listingId).orElseThrow().getOwner().getId();
        LocalDate d = AppTime.today(clock).plusDays(10);
        Long guestId = guest();

        List<Boolean> results = race(
                () -> bookingService.createAccommodation(guestId,
                        new AccommodationBookingRequest(listingId, d, d.plusDays(1), 2, null)),
                () -> unavailablePeriodService.create(ownerId, listingId, new UnavailablePeriodRequest(d, d, null)));

        assertThat(results).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void parallelTourBookingsDoNotOverfillTheDate() throws Exception {
        Long tourId = approved(support.createTour(owner));
        Long dateId = createDate(tourId, AppTime.today(clock).plusDays(20));
        List<Callable<Object>> bookings = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Long guestId = guest();
            bookings.add(() -> bookingService.createTour(guestId, new TourBookingRequest(dateId, 5, null)));
        }

        // 12 places: two groups of 5 fit, the third does not
        assertThat(race(bookings.toArray(Callable[]::new))).containsExactlyInAnyOrder(true, true, false);
        assertThat(jdbc.queryForObject("select booked_count from tour_date where id = ?", Integer.class, dateId))
                .isEqualTo(10);
    }

    // starts all tasks at the same moment; true = succeeded, false = rejected with 409
    @SafeVarargs
    private static List<Boolean> race(Callable<Object>... tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.call();
                        return true;
                    } catch (ConflictException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    // approval written directly, so no status history row has to be cleaned up
    private Long approved(Long listingId) {
        listingIds.add(listingId);
        Listing listing = listingRepo.findById(listingId).orElseThrow();
        listing.setStatus(ListingStatus.APPROVED);
        listingRepo.save(listing);
        return listingId;
    }

    private Long guest() {
        User user = new User();
        user.setEmail("test-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-used");
        user.setFirstName("Gost");
        user.setLastName("Gostić");
        user.setRole(Role.USER);
        user.setTermsVersion(terms.version());
        user.setTermsAcceptedAt(Instant.now());
        Long id = userRepo.save(user).getId();
        userIds.add(id);
        return id;
    }

    private Long createDate(Long tourId, LocalDate start) throws Exception {
        String body = mvc.perform(post(OWNER + "/tours/" + tourId + "/dates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\": \"%s\", \"minGuestsDeadlineDaysBefore\": 2}".formatted(start)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
