package com.villagebooker.controller.booking;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.Listing;
import com.villagebooker.model.TourDate;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.tour.TourDateRepo;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Creating bookings. accommodationJson(): 1..4 guests, 5000 per night for 2 guests, +1000 per extra guest;
// tourJson(): 2 days, 2..12 guests per date, 3500 per person. Both instant, ON_SITE, NON_REFUNDABLE.
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingFlowTest {

    private static final String BOOKINGS = "/api/bookings";
    private static final String DEFAULT_TERMS = "\"cancellationPolicy\": \"NON_REFUNDABLE\", \"paymentMethod\": \"ON_SITE\"";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ListingRepo listingRepo;

    @Autowired
    private TourDateRepo tourDateRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private Clock clock;

    private ListingTestSupport support;
    private String owner;
    private String admin;
    private String guest;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        owner = support.ownerToken();
        admin = support.adminToken();
        guest = support.userToken();
    }

    @Test
    void instantBookingIsConfirmedWithPriceAndTermsCopied() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        LocalDate arrival = today().plusDays(10);

        bookAccommodation(guest, listingId, arrival, arrival.plusDays(2), 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.listingType").value("ACCOMMODATION"))
                .andExpect(jsonPath("$.startDate").value(arrival.toString()))
                .andExpect(jsonPath("$.endDate").value(arrival.plusDays(1).toString()))
                .andExpect(jsonPath("$.departureDate").value(arrival.plusDays(2).toString()))
                // (5000 + 1 extra guest * 1000) * 2 nights
                .andExpect(jsonPath("$.totalPrice").value(12000))
                .andExpect(jsonPath("$.amountDue").value(12000))
                .andExpect(jsonPath("$.cancellationPolicy").value("NON_REFUNDABLE"))
                .andExpect(jsonPath("$.freeCancellationUntil").doesNotExist())
                .andExpect(jsonPath("$.paymentMethod").value("ON_SITE"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void ownerApprovalAndDepositStartWithAnExpiringStatus() throws Exception {
        LocalDate arrival = today().plusDays(20);

        Long manual = approved(support.create(owner, "/accommodations",
                ListingTestSupport.accommodationJson().replace("\"minGuests\"", "\"requiresOwnerApproval\": true, \"minGuests\"")));
        Instant before = Instant.now();
        String body = bookAccommodation(guest, manual, arrival, arrival.plusDays(1), 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_OWNER_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        assertExpiresAfter(body, before, Duration.ofHours(24));

        Long deposit = approved(support.create(owner, "/accommodations", ListingTestSupport.accommodationJson()
                .replace(DEFAULT_TERMS, "\"cancellationPolicy\": \"FREE\", \"freeCancellationDays\": 7, "
                        + "\"paymentMethod\": \"ONLINE\", \"depositPercentage\": 30")));
        before = Instant.now();
        body = bookAccommodation(guest, deposit, arrival, arrival.plusDays(1), 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.depositPercentage").value(30))
                .andExpect(jsonPath("$.freeCancellationUntil").value(AppTime.startOfDay(arrival.minusDays(7)).toString()))
                .andReturn().getResponse().getContentAsString();
        assertExpiresAfter(body, before, Duration.ofMinutes(15));
    }

    @Test
    void takenNightsAreRejectedButTheDepartureDayIsFree() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        LocalDate d = today().plusDays(10);

        bookAccommodation(guest, listingId, d, d.plusDays(2), 2).andExpect(status().isCreated());
        bookAccommodation(support.userToken(), listingId, d.plusDays(1), d.plusDays(3), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATES_NOT_AVAILABLE"));
        bookAccommodation(support.userToken(), listingId, d.plusDays(2), d.plusDays(4), 2)
                .andExpect(status().isCreated());

        mvc.perform(post(OWNER + "/" + listingId + "/unavailable-periods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\": \"%s\", \"endDate\": \"%s\"}".formatted(d.plusDays(10), d.plusDays(10))))
                .andExpect(status().isCreated());
        bookAccommodation(guest, listingId, d.plusDays(9), d.plusDays(12), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATES_NOT_AVAILABLE"));
    }

    @Test
    void onlyApprovedListingsOfOtherHostsAreBookable() throws Exception {
        LocalDate d = today().plusDays(10);
        Long pending = support.createAccommodation(owner);
        bookAccommodation(guest, pending, d, d.plusDays(1), 2)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));

        Long listingId = approved(pending);
        bookAccommodation(owner, listingId, d, d.plusDays(1), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_BOOK_OWN_LISTING"));

        Listing listing = listingRepo.findById(listingId).orElseThrow();
        listing.setStatus(ListingStatus.PAUSED_BY_OWNER);
        listingRepo.saveAndFlush(listing);
        bookAccommodation(guest, listingId, d, d.plusDays(1), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_BOOKABLE"));

        Long tourId = approved(support.createTour(owner));
        bookAccommodation(guest, tourId, d, d.plusDays(1), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LISTING_TYPE_MISMATCH"));
    }

    @Test
    void guestCountAndDatesAreChecked() throws Exception {
        Long listingId = approved(support.createAccommodation(owner));
        LocalDate today = today();

        bookAccommodation(guest, listingId, today.plusDays(5), today.plusDays(6), 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUEST_COUNT_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.params.max").value(4));
        bookAccommodation(guest, listingId, today.plusDays(5), today.plusDays(5), 2)
                .andExpect(jsonPath("$.code").value("BOOKING_DATES_INVALID"));
        bookAccommodation(guest, listingId, today.minusDays(1), today.plusDays(1), 2)
                .andExpect(jsonPath("$.code").value("BOOKING_DATES_INVALID"));
        bookAccommodation(guest, listingId, today.plusDays(365), today.plusDays(367), 2)
                .andExpect(jsonPath("$.code").value("BOOKING_DATES_INVALID"));
        bookAccommodation(guest, listingId, today, today.plusDays(1), 2).andExpect(status().isCreated());
    }

    @Test
    void tourBookingsFillTheDate() throws Exception {
        Long tourId = approved(support.createTour(owner));
        Long dateId = createDate(tourId, today().plusDays(20));

        bookTour(guest, dateId, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingType").value("TOUR"))
                .andExpect(jsonPath("$.tourDateId").value(dateId))
                .andExpect(jsonPath("$.endDate").value(today().plusDays(21).toString()))
                .andExpect(jsonPath("$.departureDate").doesNotExist())
                .andExpect(jsonPath("$.totalPrice").value(3500));
        assertDate(dateId, 1, TourDateStatus.OPEN);

        bookTour(support.userToken(), dateId, 1).andExpect(status().isCreated());
        assertDate(dateId, 2, TourDateStatus.CONFIRMED);

        bookTour(support.userToken(), dateId, 11)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOUR_DATE_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.params.freePlaces").value(10));
        bookTour(support.userToken(), dateId, 10).andExpect(status().isCreated());
        assertDate(dateId, 12, TourDateStatus.FULL);

        bookTour(support.userToken(), dateId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOUR_DATE_NOT_BOOKABLE"));
        bookTour(guest, 999999999L, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOUR_DATE_NOT_FOUND"));
    }

    private LocalDate today() {
        return AppTime.today(clock);
    }

    private Long approved(Long listingId) throws Exception {
        support.approveListing(admin, listingId).andExpect(status().isOk());
        return listingId;
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

    private void assertDate(Long dateId, int bookedCount, TourDateStatus status) {
        TourDate date = tourDateRepo.findById(dateId).orElseThrow();
        assertThat(date.getBookedCount()).isEqualTo(bookedCount);
        assertThat(date.getStatus()).isEqualTo(status);
    }

    private static void assertExpiresAfter(String body, Instant before, Duration timeout) {
        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(expiresAt).isBetween(before.plus(timeout), Instant.now().plus(timeout));
    }

    private ResultActions bookAccommodation(String token, Long listingId, LocalDate arrival, LocalDate departure,
                                            int guests) throws Exception {
        return mvc.perform(post(BOOKINGS + "/accommodations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"listingId\": %d, \"arrival\": \"%s\", \"departure\": \"%s\", \"guestCount\": %d}"
                        .formatted(listingId, arrival, departure, guests)));
    }

    private ResultActions bookTour(String token, Long dateId, int guests) throws Exception {
        return mvc.perform(post(BOOKINGS + "/tours")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tourDateId\": %d, \"guestCount\": %d}".formatted(dateId, guests)));
    }
}
