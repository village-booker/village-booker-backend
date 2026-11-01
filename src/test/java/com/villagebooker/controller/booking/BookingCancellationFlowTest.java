package com.villagebooker.controller.booking;

import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.enums.payment.PaymentChannel;
import com.villagebooker.enums.payment.PaymentStatus;
import com.villagebooker.enums.payment.PaymentType;
import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.model.Payment;
import com.villagebooker.model.TourDate;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.payment.PaymentRepo;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static com.villagebooker.controller.booking.BookingTestSupport.BOOKINGS;
import static com.villagebooker.controller.booking.BookingTestSupport.OWNER_BOOKINGS;
import static com.villagebooker.controller.booking.BookingTestSupport.withTerms;
import static com.villagebooker.controller.listing.ListingTestSupport.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cancelling and refunds (spec 3, 9.2). Accommodation: 5000 per night for 2 guests, so 2 nights = 10000 and a
// 30 % deposit = 3000; tour: 3500 per person, 2..12 guests per date. Payments are simulated.
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest(properties = "app.payments.simulated=true")
@AutoConfigureMockMvc
@Transactional
class BookingCancellationFlowTest {

    private static final String DEPOSIT = "\"paymentMethod\": \"ONLINE\", \"depositPercentage\": 30";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private BookingRepo bookingRepo;

    @Autowired
    private PaymentRepo paymentRepo;

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
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        support = new ListingTestSupport(mvc, userRepo, jwtService, terms);
        bookings = new BookingTestSupport(mvc);
        owner = support.ownerToken();
        admin = support.adminToken();
        guest = support.userToken();
        today = AppTime.today(clock);
    }

    @Test
    void guestCancelsInTheFreePeriodAndGetsEverythingBack() throws Exception {
        Long listing = accommodation("\"cancellationPolicy\": \"FREE\", \"freeCancellationDays\": 7, " + DEPOSIT);
        LocalDate arrival = today.plusDays(20);
        Long bookingId = paidBooking(listing, arrival);

        preview(guest, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paidAmount").value(3000))
                .andExpect(jsonPath("$.refundAmount").value(3000));
        // the preview changes nothing
        assertThat(bookingRepo.findById(bookingId).orElseThrow().getCancelledAt()).isNull();

        cancelAsGuest(guest, bookingId, "Promena planova")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CANCELLED_BY_GUEST"))
                .andExpect(jsonPath("$.booking.cancelledAt").exists())
                .andExpect(jsonPath("$.booking.cancellationReason").value("Promena planova"))
                .andExpect(jsonPath("$.paidAmount").value(3000))
                .andExpect(jsonPath("$.refundAmount").value(3000));
        assertPayments(bookingId, refunded("3000", "3000"));

        // the nights are free again
        bookings.bookAccommodation(support.userToken(), listing, arrival, arrival.plusDays(2), 2)
                .andExpect(status().isCreated());
        cancelAsGuest(guest, bookingId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_STATUS_INVALID"));
        preview(guest, bookingId).andExpect(status().isConflict());
    }

    @Test
    void freePolicyAfterTheDeadlineRefundsNothing() throws Exception {
        // 5 days before arrival: the 7-day deadline has already passed at booking time
        Long listing = accommodation("\"cancellationPolicy\": \"FREE\", \"freeCancellationDays\": 7, " + DEPOSIT);
        Long bookingId = paidBooking(listing, today.plusDays(5));

        cancelAsGuest(guest, bookingId, null)
                .andExpect(jsonPath("$.booking.status").value("CANCELLED_BY_GUEST"))
                .andExpect(jsonPath("$.paidAmount").value(3000))
                .andExpect(jsonPath("$.refundAmount").value(0));
        assertPayments(bookingId, succeeded("3000"));
    }

    @Test
    void partialPolicyAfterTheDeadlineRefundsThePercentage() throws Exception {
        Long listing = accommodation(
                "\"cancellationPolicy\": \"PARTIAL\", \"refundPercentage\": 40, \"freeCancellationDays\": 7, " + DEPOSIT);
        Long bookingId = paidBooking(listing, today.plusDays(5));

        preview(guest, bookingId).andExpect(jsonPath("$.refundAmount").value(1200));
        cancelAsGuest(guest, bookingId, null).andExpect(jsonPath("$.refundAmount").value(1200));
        assertPayments(bookingId, refunded("3000", "1200"));
    }

    @Test
    void refundIsSpreadOverPaymentsInOrder() throws Exception {
        Long listing = accommodation(
                "\"cancellationPolicy\": \"PARTIAL\", \"refundPercentage\": 40, \"freeCancellationDays\": 7, " + DEPOSIT);
        Long bookingId = paidBooking(listing, today.plusDays(5));
        // the rest paid on site, recorded by hand (no endpoint for that yet)
        Payment balance = new Payment();
        balance.setBooking(bookingRepo.findById(bookingId).orElseThrow());
        balance.setAmount(new BigDecimal("7000.00"));
        balance.setChannel(PaymentChannel.ON_SITE);
        balance.setType(PaymentType.BALANCE);
        balance.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepo.saveAndFlush(balance);

        // 40 % of 10000: the whole deposit (3000) and 1000 of the balance
        cancelAsGuest(guest, bookingId, null)
                .andExpect(jsonPath("$.paidAmount").value(10000))
                .andExpect(jsonPath("$.refundAmount").value(4000));
        assertPayments(bookingId, refunded("3000", "3000"), refunded("7000", "1000"));
    }

    @Test
    void nonRefundableGivesNothingBackToTheGuest() throws Exception {
        Long listing = accommodation("\"cancellationPolicy\": \"NON_REFUNDABLE\", " + DEPOSIT);
        Long bookingId = paidBooking(listing, today.plusDays(30));

        cancelAsGuest(guest, bookingId, null).andExpect(jsonPath("$.refundAmount").value(0));
        assertPayments(bookingId, succeeded("3000"));
    }

    @Test
    void hostCancellationRefundsEverythingWhateverThePolicy() throws Exception {
        Long listing = accommodation("\"cancellationPolicy\": \"NON_REFUNDABLE\", " + DEPOSIT);
        Long bookingId = paidBooking(listing, today.plusDays(3));

        cancelAsOwner(owner, bookingId, "Poplava u kući")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CANCELLED_BY_OWNER"))
                .andExpect(jsonPath("$.refundAmount").value(3000));
        assertPayments(bookingId, refunded("3000", "3000"));
        mvc.perform(get(BOOKINGS + "/" + bookingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + guest))
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_OWNER"))
                .andExpect(jsonPath("$.cancellationReason").value("Poplava u kući"));
    }

    @Test
    void hostHasToGiveAReason() throws Exception {
        Long listing = accommodation("\"cancellationPolicy\": \"NON_REFUNDABLE\", \"paymentMethod\": \"ON_SITE\"");
        Long bookingId = bookings.book(guest, listing, today.plusDays(10), today.plusDays(11), 2);

        cancelAsOwner(owner, bookingId, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason.code").value("NotBlank"));
        mvc.perform(post(OWNER_BOOKINGS + "/" + bookingId + "/cancel").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isBadRequest());
        cancelAsGuest(guest, bookingId, "x".repeat(256))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason.code").value("Size"));
    }

    @Test
    void unpaidBookingsAreCancelledWithoutRefund() throws Exception {
        Long onSite = support.createAccommodation(owner);
        support.approveListing(admin, onSite).andExpect(status().isOk());
        Long confirmed = bookings.bookApproved(guest, owner, onSite, today.plusDays(10), today.plusDays(11), 2);
        cancelAsGuest(guest, confirmed, null)
                .andExpect(jsonPath("$.paidAmount").value(0))
                .andExpect(jsonPath("$.refundAmount").value(0));

        // a request the host has not answered yet: its deadline goes away with it
        Long manual = accommodation(null);
        Long request = bookings.book(guest, manual, today.plusDays(10), today.plusDays(11), 2);
        mvc.perform(post(BOOKINGS + "/" + request + "/cancel").header(HttpHeaders.AUTHORIZATION, "Bearer " + guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CANCELLED_BY_GUEST"))
                .andExpect(jsonPath("$.booking.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.booking.cancellationReason").doesNotExist());
    }

    @Test
    void startedBookingsAreNotCancelled() throws Exception {
        Long listing = support.createAccommodation(owner);
        support.approveListing(admin, listing).andExpect(status().isOk());
        Long tonight = bookings.bookApproved(guest, owner, listing, today.plusDays(1), today.plusDays(3), 2);
        Booking booking = bookingRepo.findById(tonight).orElseThrow();
        booking.setStartDate(today);
        bookingRepo.saveAndFlush(booking);

        cancelAsGuest(guest, tonight, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_STARTED"))
                .andExpect(jsonPath("$.params.startDate").value(today.toString()));
        preview(guest, tonight).andExpect(jsonPath("$.code").value("BOOKING_ALREADY_STARTED"));
        cancelAsOwner(owner, tonight, "Kasno").andExpect(jsonPath("$.code").value("BOOKING_ALREADY_STARTED"));
    }

    @Test
    void onlyOwnBookingsAreCancelled() throws Exception {
        Long listing = support.createAccommodation(owner);
        support.approveListing(admin, listing).andExpect(status().isOk());
        Long bookingId = bookings.book(guest, listing, today.plusDays(10), today.plusDays(11), 2);

        cancelAsGuest(support.userToken(), bookingId, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        preview(support.userToken(), bookingId).andExpect(status().isNotFound());
        cancelAsOwner(support.ownerToken(), bookingId, "Nije moje").andExpect(status().isNotFound());
        cancelAsOwner(guest, bookingId, "Gost nije domaćin").andExpect(status().isForbidden());
    }

    @Test
    void cancelledTourBookingGivesThePlacesBack() throws Exception {
        Long tourId = tour("\"cancellationPolicy\": \"NON_REFUNDABLE\", \"paymentMethod\": \"ON_SITE\"");
        Long dateId = bookings.createDate(owner, tourId, today.plusDays(20));
        Long first = BookingTestSupport.idOf(bookings.bookTour(guest, dateId, 2).andExpect(status().isCreated()));
        bookings.bookTour(support.userToken(), dateId, 10).andExpect(status().isCreated());
        assertDate(dateId, 12, TourDateStatus.FULL);

        cancelAsGuest(guest, first, null).andExpect(status().isOk());
        assertDate(dateId, 10, TourDateStatus.CONFIRMED);
        bookings.bookTour(support.userToken(), dateId, 2).andExpect(status().isCreated());
    }

    @Test
    void hostCancelsAWholeTourDate() throws Exception {
        Long tourId = tour("\"cancellationPolicy\": \"NON_REFUNDABLE\", " + DEPOSIT);
        Long dateId = bookings.createDate(owner, tourId, today.plusDays(20));
        // 2 * 3500 = 7000, deposit 2100
        Long paid = BookingTestSupport.idOf(bookings.bookTour(guest, dateId, 2).andExpect(status().isCreated()));
        bookings.approve(owner, paid).andExpect(status().isOk());
        bookings.pay(guest, paid).andExpect(jsonPath("$.status").value("CONFIRMED"));
        Long unpaid = BookingTestSupport.idOf(bookings.bookTour(support.userToken(), dateId, 3).andExpect(status().isCreated()));
        String leaver = support.userToken();
        Long left = BookingTestSupport.idOf(bookings.bookTour(leaver, dateId, 1).andExpect(status().isCreated()));
        cancelAsGuest(leaver, left, null).andExpect(status().isOk());
        assertDate(dateId, 5, TourDateStatus.CONFIRMED);

        cancelDate(owner, tourId, dateId, "Nevreme na Drini")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.bookedCount").value(0));

        assertBooking(paid, "CANCELLED_BY_OWNER", "Nevreme na Drini");
        assertBooking(unpaid, "CANCELLED_BY_OWNER", "Nevreme na Drini");
        assertBooking(left, "CANCELLED_BY_GUEST", null);
        // the host cancelled: the whole deposit back, even though the tour is non-refundable
        assertPayments(paid, refunded("2100", "2100"));
        bookings.bookTour(support.userToken(), dateId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOUR_DATE_NOT_BOOKABLE"));

        // once more: nothing changes
        cancelDate(owner, tourId, dateId, "Opet").andExpect(jsonPath("$.status").value("CANCELLED"));
        assertBooking(paid, "CANCELLED_BY_OWNER", "Nevreme na Drini");
    }

    @Test
    void tourDateCancellationIsChecked() throws Exception {
        Long tourId = tour("\"cancellationPolicy\": \"NON_REFUNDABLE\", \"paymentMethod\": \"ON_SITE\"");
        Long dateId = bookings.createDate(owner, tourId, today.plusDays(20));
        Long otherTour = tour("\"cancellationPolicy\": \"NON_REFUNDABLE\", \"paymentMethod\": \"ON_SITE\"");

        cancelDate(owner, tourId, dateId, " ").andExpect(status().isBadRequest());
        cancelDate(owner, otherTour, dateId, "Pogrešna tura")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOUR_DATE_NOT_FOUND"));
        cancelDate(support.ownerToken(), tourId, dateId, "Tuđa tura")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_LISTING_OWNER"));

        TourDate date = tourDateRepo.findById(dateId).orElseThrow();
        date.setStartDate(today);
        tourDateRepo.saveAndFlush(date);
        cancelDate(owner, tourId, dateId, "Kasno")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOUR_DATE_IN_PAST"));
    }

    // null terms = accommodationJson()
    private Long accommodation(String terms) throws Exception {
        String json = terms == null ? ListingTestSupport.accommodationJson()
                : withTerms(ListingTestSupport.accommodationJson(), terms);
        Long id = support.create(owner, "/accommodations", json);
        support.approveListing(admin, id).andExpect(status().isOk());
        return id;
    }

    private Long tour(String terms) throws Exception {
        Long id = support.create(owner, "/tours", withTerms(ListingTestSupport.tourJson(), terms));
        support.approveListing(admin, id).andExpect(status().isOk());
        return id;
    }

    // 2 nights for 2 guests with the 30 % deposit paid
    private Long paidBooking(Long listing, LocalDate arrival) throws Exception {
        Long id = bookings.bookApproved(guest, owner, listing, arrival, arrival.plusDays(2), 2);
        bookings.pay(guest, id).andExpect(jsonPath("$.status").value("CONFIRMED"));
        return id;
    }

    private record ExpectedPayment(PaymentStatus status, String amount, String refundAmount) {
    }

    private static ExpectedPayment refunded(String amount, String refundAmount) {
        return new ExpectedPayment(PaymentStatus.REFUNDED, amount, refundAmount);
    }

    private static ExpectedPayment succeeded(String amount) {
        return new ExpectedPayment(PaymentStatus.SUCCEEDED, amount, "0");
    }

    private void assertPayments(Long bookingId, ExpectedPayment... expected) {
        List<Payment> payments = paymentRepo.findByBookingId(bookingId).stream()
                .sorted(Comparator.comparing(Payment::getId)).toList();
        assertThat(payments).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(payments.get(i).getStatus()).isEqualTo(expected[i].status());
            assertThat(payments.get(i).getAmount()).isEqualByComparingTo(expected[i].amount());
            assertThat(payments.get(i).getRefundAmount()).isEqualByComparingTo(expected[i].refundAmount());
        }
    }

    private void assertBooking(Long bookingId, String status, String reason) {
        Booking booking = bookingRepo.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus().name()).isEqualTo(status);
        assertThat(booking.getCancellationReason()).isEqualTo(reason);
        assertThat(booking.getCancelledAt()).isNotNull();
    }

    private void assertDate(Long dateId, int bookedCount, TourDateStatus status) {
        TourDate date = tourDateRepo.findById(dateId).orElseThrow();
        assertThat(date.getBookedCount()).isEqualTo(bookedCount);
        assertThat(date.getStatus()).isEqualTo(status);
    }

    private ResultActions preview(String token, Long bookingId) throws Exception {
        return mvc.perform(get(BOOKINGS + "/" + bookingId + "/cancellation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions cancelAsGuest(String token, Long bookingId, String reason) throws Exception {
        return mvc.perform(post(BOOKINGS + "/" + bookingId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reason == null ? "{}" : "{\"reason\": \"%s\"}".formatted(reason)));
    }

    private ResultActions cancelAsOwner(String token, Long bookingId, String reason) throws Exception {
        return mvc.perform(post(OWNER_BOOKINGS + "/" + bookingId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"%s\"}".formatted(reason)));
    }

    private ResultActions cancelDate(String token, Long tourId, Long dateId, String reason) throws Exception {
        return mvc.perform(post(OWNER + "/tours/" + tourId + "/dates/" + dateId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"%s\"}".formatted(reason)));
    }
}
