package com.villagebooker.controller.booking;

import com.jayway.jsonpath.JsonPath;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.model.TourDate;
import com.villagebooker.repo.booking.BookingRepo;
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

import static com.villagebooker.controller.booking.BookingTestSupport.OWNER_BOOKINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The host's side: bookings of own listings, approving and declining requests (listings: see BookingTestSupport).
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnerBookingFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private BookingRepo bookingRepo;

    @Autowired
    private TourDateRepo tourDateRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private Clock clock;

    private ListingTestSupport support;
    private BookingTestSupport bookings;
    private String owner;
    private String admin;
    private String guest;
    private LocalDate d;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        bookings = new BookingTestSupport(mvc);
        owner = support.ownerToken();
        admin = support.adminToken();
        guest = support.userToken();
        d = AppTime.today(clock).plusDays(10);
    }

    @Test
    void hostSeesBookingsOfOwnListingsWithFilters() throws Exception {
        Long cabin = approved(support.createAccommodation(owner));
        Long manual = approved(support.create(owner, "/accommodations", BookingTestSupport.manualApprovalJson()));
        Long confirmed = bookings.book(guest, cabin, d, d.plusDays(1), 2);
        Long request = bookings.book(guest, manual, d, d.plusDays(1), 2);
        String otherHost = support.ownerToken();
        Long otherListing = approved(support.createAccommodation(otherHost));
        Long notMine = bookings.book(guest, otherListing, d, d.plusDays(1), 2);

        list(owner, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].booking.id").value(request))
                .andExpect(jsonPath("$.content[0].guest.firstName").value("Ana"))
                .andExpect(jsonPath("$.content[0].guest.email").exists())
                .andExpect(jsonPath("$.content[1].booking.id").value(confirmed));
        list(owner, "?status=AWAITING_OWNER_APPROVAL")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].booking.id").value(request));
        list(owner, "?listingId=" + cabin)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].booking.id").value(confirmed));
        list(owner, "?status=CONFIRMED&listingId=" + manual)
                .andExpect(jsonPath("$.totalElements").value(0));
        list(owner, "?status=NOT_A_STATUS").andExpect(status().isBadRequest());

        mvc.perform(get(OWNER_BOOKINGS + "/" + request).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("AWAITING_OWNER_APPROVAL"))
                .andExpect(jsonPath("$.guest.lastName").value("Anić"));
        mvc.perform(get(OWNER_BOOKINGS + "/" + notMine).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        approve(owner, notMine).andExpect(status().isNotFound());
    }

    @Test
    void guestsCannotUseTheHostRoutes() throws Exception {
        Long manual = approved(support.create(owner, "/accommodations", BookingTestSupport.manualApprovalJson()));
        Long request = bookings.book(guest, manual, d, d.plusDays(1), 2);

        list(guest, "").andExpect(status().isForbidden());
        approve(guest, request).andExpect(status().isForbidden());
        reject(guest, request, null).andExpect(status().isForbidden());
    }

    @Test
    void approvalWithoutDepositConfirmsTheBooking() throws Exception {
        Long manual = approved(support.create(owner, "/accommodations", BookingTestSupport.manualApprovalJson()));
        Long request = bookings.book(guest, manual, d, d.plusDays(1), 2);

        approve(owner, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.booking.expiresAt").doesNotExist());
        approve(owner, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_STATUS_INVALID"))
                .andExpect(jsonPath("$.params.status").value("CONFIRMED"));
        reject(owner, request, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_STATUS_INVALID"));
    }

    @Test
    void approvalWithDepositWaitsForThePayment() throws Exception {
        Long listing = approved(support.create(owner, "/accommodations",
                BookingTestSupport.manualApprovalWithDepositJson()));
        Long request = bookings.book(guest, listing, d, d.plusDays(1), 2);

        Instant before = Instant.now();
        String body = approve(owner, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.booking.depositAmount").value(1500))
                .andReturn().getResponse().getContentAsString();
        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.booking.expiresAt"));
        assertThat(expiresAt).isBetween(before.plus(Duration.ofMinutes(15)), Instant.now().plus(Duration.ofMinutes(15)));

        // the host cannot skip the payment by approving once more
        approve(owner, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_STATUS_INVALID"))
                .andExpect(jsonPath("$.params.status").value("AWAITING_PAYMENT"));
    }

    @Test
    void instantBookingsAreNotApprovedOrDeclined() throws Exception {
        Long cabin = approved(support.createAccommodation(owner));
        Long confirmed = bookings.book(guest, cabin, d, d.plusDays(1), 2);

        approve(owner, confirmed).andExpect(status().isConflict());
        reject(owner, confirmed, "Ne").andExpect(status().isConflict());
    }

    @Test
    void declinedRequestFreesTheNightsAndShowsTheReason() throws Exception {
        Long manual = approved(support.create(owner, "/accommodations", BookingTestSupport.manualApprovalJson()));
        Long request = bookings.book(guest, manual, d, d.plusDays(2), 2);
        bookings.bookAccommodation(support.userToken(), manual, d.plusDays(1), d.plusDays(2), 2)
                .andExpect(status().isConflict());

        reject(owner, request, "  Smeštaj je zauzet za porodicu.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("REJECTED"))
                .andExpect(jsonPath("$.booking.cancellationReason").value("Smeštaj je zauzet za porodicu."))
                .andExpect(jsonPath("$.booking.expiresAt").doesNotExist());
        mvc.perform(get(BookingTestSupport.BOOKINGS + "/" + request).header(HttpHeaders.AUTHORIZATION, "Bearer " + guest))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.cancellationReason").value("Smeštaj je zauzet za porodicu."));

        bookings.bookAccommodation(support.userToken(), manual, d.plusDays(1), d.plusDays(2), 2)
                .andExpect(status().isCreated());
    }

    @Test
    void reasonIsOptionalButLimited() throws Exception {
        Long manual = approved(support.create(owner, "/accommodations", BookingTestSupport.manualApprovalJson()));
        Long first = bookings.book(guest, manual, d, d.plusDays(1), 2);
        Long second = bookings.book(guest, manual, d.plusDays(5), d.plusDays(6), 2);

        reject(owner, first, "x".repeat(256))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason.code").value("Size"));
        mvc.perform(post(OWNER_BOOKINGS + "/" + first + "/reject").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.cancellationReason").doesNotExist());
        reject(owner, second, "   ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.cancellationReason").doesNotExist());
    }

    @Test
    void declinedTourRequestGivesThePlacesBack() throws Exception {
        Long tourId = approved(support.create(owner, "/tours", ListingTestSupport.tourJson()
                .replace("\"minGuests\"", "\"requiresOwnerApproval\": true, \"minGuests\"")));
        Long dateId = bookings.createDate(owner, tourId, d.plusDays(10));
        Long first = BookingTestSupport.idOf(bookings.bookTour(guest, dateId, 1).andExpect(status().isCreated()));
        bookings.bookTour(support.userToken(), dateId, 1).andExpect(status().isCreated());
        assertDate(dateId, 2, TourDateStatus.CONFIRMED);

        reject(owner, first, null).andExpect(jsonPath("$.booking.status").value("REJECTED"));

        // below the minimum (2) before the deadline: open again
        assertDate(dateId, 1, TourDateStatus.OPEN);
    }

    @Test
    void expiredRequestCannotBeApprovedButCanBeDeclined() throws Exception {
        Long manual = approved(support.create(owner, "/accommodations", BookingTestSupport.manualApprovalJson()));
        Long request = bookings.book(guest, manual, d, d.plusDays(1), 2);
        Booking booking = bookingRepo.findById(request).orElseThrow();
        booking.setExpiresAt(Instant.now().minusSeconds(1));
        bookingRepo.saveAndFlush(booking);

        approve(owner, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_EXPIRED"));
        reject(owner, request, null).andExpect(jsonPath("$.booking.status").value("REJECTED"));
    }

    private Long approved(Long listingId) throws Exception {
        support.approveListing(admin, listingId).andExpect(status().isOk());
        return listingId;
    }

    private void assertDate(Long dateId, int bookedCount, TourDateStatus status) {
        TourDate date = tourDateRepo.findById(dateId).orElseThrow();
        assertThat(date.getBookedCount()).isEqualTo(bookedCount);
        assertThat(date.getStatus()).isEqualTo(status);
    }

    private ResultActions list(String token, String query) throws Exception {
        return mvc.perform(get(OWNER_BOOKINGS + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions approve(String token, Long bookingId) throws Exception {
        return mvc.perform(post(OWNER_BOOKINGS + "/" + bookingId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions reject(String token, Long bookingId, String reason) throws Exception {
        return mvc.perform(post(OWNER_BOOKINGS + "/" + bookingId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reason == null ? "{}" : "{\"reason\": \"%s\"}".formatted(reason)));
    }
}
