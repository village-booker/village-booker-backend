package com.villagebooker.controller.listing;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.CancellationPolicy;
import com.villagebooker.enums.payment.PaymentMethod;
import com.villagebooker.enums.user.Role;
import com.villagebooker.model.Booking;
import com.villagebooker.model.User;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UnavailablePeriodFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ListingRepo listingRepo;

    @Autowired
    private BookingRepo bookingRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private Clock clock;

    private ListingTestSupport support;
    private String owner;
    private Long listingId;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        owner = support.ownerToken();
        listingId = support.createAccommodation(owner);
    }

    @Test
    void ownerBlocksNightsAndGuestsSeeThemTaken() throws Exception {
        LocalDate d = today().plusDays(10);
        Long id = idOf(block(owner, d, d.plusDays(2), "Renoviranje")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("Renoviranje")));
        support.approveListing(support.adminToken(), listingId).andExpect(status().isOk());

        mvc.perform(get(periods()).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/listings/" + listingId + "/availability"))
                .andExpect(jsonPath("$.takenNights[0].startDate").value(d.toString()))
                .andExpect(jsonPath("$.takenNights[0].reason").doesNotExist());

        mvc.perform(delete(periods() + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/listings/" + listingId + "/availability"))
                .andExpect(jsonPath("$.takenNights.length()").value(0));
    }

    @Test
    void blockCannotTakeABookedNightButMayOverlapAnotherBlock() throws Exception {
        LocalDate d = today().plusDays(10);
        book(d, d.plusDays(1));

        // d+1 is the last booked night
        block(owner, d.plusDays(1), d.plusDays(3), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERIOD_OVERLAPS_BOOKING"));
        // d+2 is the departure day of the booking, so it is free
        block(owner, d.plusDays(2), d.plusDays(4), null).andExpect(status().isCreated());
        block(owner, d.plusDays(3), d.plusDays(5), null).andExpect(status().isCreated());
    }

    @Test
    void datesMustBeAheadAndInOrder() throws Exception {
        LocalDate today = today();
        block(owner, today.minusDays(1), today.plusDays(1), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"));
        block(owner, today.plusDays(5), today.plusDays(4), null)
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"));
        block(owner, today.plusDays(300), today.plusDays(400), null)
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"));
        block(owner, today, today, null).andExpect(status().isCreated());
    }

    @Test
    void onlyOwnAccommodations() throws Exception {
        LocalDate d = today().plusDays(10);
        block(support.ownerToken(), d, d, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_LISTING_OWNER"));

        Long tour = support.createTour(owner);
        mvc.perform(post(OWNER + "/" + tour + "/unavailable-periods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(d, d, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LISTING_TYPE_MISMATCH"));

        mvc.perform(delete(periods() + "/999999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNAVAILABLE_PERIOD_NOT_FOUND"));
    }

    private LocalDate today() {
        return AppTime.today(clock);
    }

    private String periods() {
        return OWNER + "/" + listingId + "/unavailable-periods";
    }

    private ResultActions block(String token, LocalDate start, LocalDate end, String reason) throws Exception {
        return mvc.perform(post(periods())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(start, end, reason)));
    }

    private static String json(LocalDate start, LocalDate end, String reason) {
        return "{\"startDate\": \"%s\", \"endDate\": \"%s\", \"reason\": %s}"
                .formatted(start, end, reason == null ? "null" : "\"" + reason + "\"");
    }

    // a confirmed booking of the nights [start, end], written directly (bookings have no endpoint yet)
    private void book(LocalDate start, LocalDate end) {
        User guest = new User();
        guest.setEmail("test-" + UUID.randomUUID() + "@example.com");
        guest.setPasswordHash("not-used");
        guest.setFirstName("Gost");
        guest.setLastName("Gostić");
        guest.setRole(Role.USER);
        guest.setTermsVersion(terms.version());
        guest.setTermsAcceptedAt(Instant.now());
        userRepo.save(guest);

        Booking booking = new Booking();
        booking.setGuest(guest);
        booking.setListing(listingRepo.getReferenceById(listingId));
        booking.setStartDate(start);
        booking.setEndDate(end);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setGuestCount(2);
        booking.setTotalPrice(new BigDecimal("10000"));
        booking.setAmountDue(BigDecimal.ZERO);
        booking.setCancellationPolicy(CancellationPolicy.NON_REFUNDABLE);
        booking.setPaymentMethod(PaymentMethod.ON_SITE);
        bookingRepo.saveAndFlush(booking);
    }

    private static Long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
