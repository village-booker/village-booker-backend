package com.villagebooker.service.booking;

import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.booking.BookingTestSupport;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.payment.PaymentStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static com.villagebooker.controller.booking.BookingTestSupport.idOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The scheduler's steps (spec 8) on real bookings. Time passing is simulated by moving expiresAt, deadlines and
// dates into the past. The jobs join the test transaction, so everything is rolled back.
@SpringBootTest(properties = "app.payments.simulated=true")
@AutoConfigureMockMvc
@Transactional
class BookingJobsFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private BookingRepo bookingRepo;

    @Autowired
    private TourDateRepo tourDateRepo;

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TermsProperties terms;

    @Autowired
    private Clock clock;

    @Autowired
    private BookingJobs jobs;

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
    void expiredRequestFreesTheNights() throws Exception {
        Long cabin = approved(support.createAccommodation(owner));
        LocalDate arrival = today.plusDays(10);
        Long request = bookings.book(guest, cabin, arrival, arrival.plusDays(2), 2);
        bookings.bookAccommodation(support.userToken(), cabin, arrival, arrival.plusDays(1), 2)
                .andExpect(status().isConflict());

        expiresAt(request, Instant.now().minusSeconds(1));
        assertThat(jobs.bookingsToExpire()).contains(request);
        jobs.expire(request);

        Booking booking = bookingRepo.findById(request).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(booking.getExpiresAt()).isNull();
        assertThat(jobs.bookingsToExpire()).doesNotContain(request);
        bookings.bookAccommodation(support.userToken(), cabin, arrival, arrival.plusDays(1), 2)
                .andExpect(status().isCreated());
    }

    // the id list can be stale: by the time the step runs, the host may have answered
    @Test
    void expiryChecksTheBookingAgain() throws Exception {
        Long cabin = approved(support.createAccommodation(owner));
        LocalDate arrival = today.plusDays(10);
        Long waiting = bookings.book(guest, cabin, arrival, arrival.plusDays(1), 2);
        Long approved = bookings.bookApproved(guest, owner, cabin, arrival.plusDays(5), arrival.plusDays(6), 2);

        jobs.expire(waiting);
        jobs.expire(approved);

        assertThat(statusOf(waiting)).isEqualTo(BookingStatus.AWAITING_OWNER_APPROVAL);
        assertThat(statusOf(approved)).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void unpaidDepositExpiresToo() throws Exception {
        Long listing = approved(support.create(owner, "/accommodations", BookingTestSupport.depositJson()));
        LocalDate arrival = today.plusDays(10);
        Long bookingId = bookings.bookApproved(guest, owner, listing, arrival, arrival.plusDays(1), 2);
        assertThat(statusOf(bookingId)).isEqualTo(BookingStatus.AWAITING_PAYMENT);

        expiresAt(bookingId, Instant.now().minusSeconds(1));
        jobs.expire(bookingId);

        assertThat(statusOf(bookingId)).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void expiredTourRequestGivesThePlacesBack() throws Exception {
        Long tourId = approved(support.createTour(owner));
        Long dateId = bookings.createDate(owner, tourId, today.plusDays(10));
        Long first = idOf(bookings.bookTour(guest, dateId, 1).andExpect(status().isCreated()));
        bookings.bookTour(support.userToken(), dateId, 1).andExpect(status().isCreated());
        assertDate(dateId, 2, TourDateStatus.CONFIRMED);

        expiresAt(first, Instant.now().minusSeconds(1));
        jobs.expire(first);

        // below the minimum (2) before the deadline: open again
        assertDate(dateId, 1, TourDateStatus.OPEN);
    }

    // A request is cut to the deadline, so at the deadline it expires and the date is counted without it:
    // only the approved booking is left, below the minimum, and the date is cancelled.
    @Test
    void requestExpiringAtTheDeadlineDoesNotSaveTheDate() throws Exception {
        Long tourId = approved(support.createTour(owner));
        Long dateId = bookings.createDate(owner, tourId, today.plusDays(10));
        Long approvedBooking = idOf(bookings.bookTour(guest, dateId, 1).andExpect(status().isCreated()));
        bookings.approve(owner, approvedBooking).andExpect(status().isOk());
        Long request = idOf(bookings.bookTour(support.userToken(), dateId, 1).andExpect(status().isCreated()));
        assertDate(dateId, 2, TourDateStatus.CONFIRMED);

        Instant deadline = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        TourDate date = tourDateRepo.findById(dateId).orElseThrow();
        date.setMinGuestsDeadline(deadline);
        tourDateRepo.saveAndFlush(date);
        expiresAt(request, deadline);

        jobs.expire(request);
        assertDate(dateId, 1, TourDateStatus.OPEN);
        assertThat(jobs.datesPastMinimumDeadline()).contains(dateId);
        jobs.cancelBelowMinimum(dateId);

        assertDate(dateId, 0, TourDateStatus.CANCELLED);
        assertThat(statusOf(approvedBooking)).isEqualTo(BookingStatus.CANCELLED_MIN_NOT_REACHED);
        assertThat(statusOf(request)).isEqualTo(BookingStatus.EXPIRED);
    }

    // deadline 0 days before = the start of the first day, so the scheduler cancels on the day the tour starts
    @Test
    void dateBelowMinimumIsCancelledWithAFullRefund() throws Exception {
        Long tourId = approved(support.create(owner, "/tours", BookingTestSupport.withTerms(ListingTestSupport.tourJson(),
                "\"cancellationPolicy\": \"NON_REFUNDABLE\", \"paymentMethod\": \"ONLINE\", \"depositPercentage\": 30")));
        Long dateId = bookings.createDate(owner, tourId, today.plusDays(10));
        Long paid = idOf(bookings.bookTour(guest, dateId, 1).andExpect(status().isCreated()));
        bookings.approve(owner, paid).andExpect(status().isOk());
        bookings.pay(guest, paid).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertDate(dateId, 1, TourDateStatus.OPEN);

        TourDate date = tourDateRepo.findById(dateId).orElseThrow();
        date.setStartDate(today);
        date.setMinGuestsDeadline(AppTime.startOfDay(today));
        tourDateRepo.saveAndFlush(date);
        Booking booking = bookingRepo.findById(paid).orElseThrow();
        booking.setStartDate(today);
        bookingRepo.saveAndFlush(booking);

        jobs.cancelBelowMinimum(dateId);

        assertDate(dateId, 0, TourDateStatus.CANCELLED);
        booking = bookingRepo.findById(paid).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED_MIN_NOT_REACHED);
        assertThat(booking.getCancelledAt()).isNotNull();
        Payment payment = paymentRepo.findByBookingId(paid).getFirst();
        // 30 % of 3500, all of it back although the tour is non-refundable
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundAmount()).isEqualByComparingTo("1050");
    }

    @Test
    void dateIsNotCancelledBeforeTheDeadlineOrWhenConfirmed() throws Exception {
        Long tourId = approved(support.createTour(owner));
        Long open = bookings.createDate(owner, tourId, today.plusDays(10));
        bookings.bookTour(guest, open, 1).andExpect(status().isCreated());
        Long confirmed = bookings.createDate(owner, tourId, today.plusDays(20));
        bookings.bookTour(guest, confirmed, 2).andExpect(status().isCreated());
        TourDate date = tourDateRepo.findById(confirmed).orElseThrow();
        date.setMinGuestsDeadline(Instant.now().minusSeconds(1));
        tourDateRepo.saveAndFlush(date);

        assertThat(jobs.datesPastMinimumDeadline()).doesNotContain(open, confirmed);
        jobs.cancelBelowMinimum(open);
        jobs.cancelBelowMinimum(confirmed);

        assertDate(open, 1, TourDateStatus.OPEN);
        assertDate(confirmed, 2, TourDateStatus.CONFIRMED);
    }

    @Test
    void confirmedBookingIsCompletedAfterItsLastDay() throws Exception {
        Long cabin = approved(support.createAccommodation(owner));
        LocalDate arrival = today.plusDays(10);
        Long stay = bookings.bookApproved(guest, owner, cabin, arrival, arrival.plusDays(2), 2);
        Long request = bookings.book(guest, cabin, arrival.plusDays(5), arrival.plusDays(6), 2);

        // the last night was yesterday: today is the departure day
        dates(stay, today.minusDays(2), today.minusDays(1));
        dates(request, today.minusDays(2), today.minusDays(1));
        assertThat(jobs.bookingsToComplete()).contains(stay).doesNotContain(request);
        jobs.complete(stay);
        jobs.complete(request);

        assertThat(statusOf(stay)).isEqualTo(BookingStatus.COMPLETED);
        assertThat(statusOf(request)).isEqualTo(BookingStatus.AWAITING_OWNER_APPROVAL);
    }

    @Test
    void bookingIsNotCompletedOnItsLastDay() throws Exception {
        Long cabin = approved(support.createAccommodation(owner));
        Long stay = bookings.bookApproved(guest, owner, cabin, today.plusDays(10), today.plusDays(11), 2);
        dates(stay, today.minusDays(1), today);

        assertThat(jobs.bookingsToComplete()).doesNotContain(stay);
        jobs.complete(stay);

        assertThat(statusOf(stay)).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void tourDateThatTookPlaceIsCompleted() throws Exception {
        Long tourId = approved(support.createTour(owner));
        Long held = bookings.createDate(owner, tourId, today.plusDays(10));
        bookings.bookTour(guest, held, 2).andExpect(status().isCreated());
        Long empty = bookings.createDate(owner, tourId, today.plusDays(20));
        for (Long dateId : new Long[]{held, empty}) {
            TourDate date = tourDateRepo.findById(dateId).orElseThrow();
            date.setStartDate(today.minusDays(2));
            date.setEndDate(today.minusDays(1));
            tourDateRepo.saveAndFlush(date);
        }

        assertThat(jobs.datesToComplete()).contains(held).doesNotContain(empty);
        jobs.completeDate(held);
        jobs.completeDate(empty);

        assertDate(held, 2, TourDateStatus.COMPLETED);
        // an OPEN date is cancelled at its deadline, never completed
        assertDate(empty, 0, TourDateStatus.OPEN);
    }

    private Long approved(Long listingId) throws Exception {
        support.approveListing(admin, listingId).andExpect(status().isOk());
        return listingId;
    }

    private void expiresAt(Long bookingId, Instant expiresAt) {
        Booking booking = bookingRepo.findById(bookingId).orElseThrow();
        booking.setExpiresAt(expiresAt);
        bookingRepo.saveAndFlush(booking);
    }

    private void dates(Long bookingId, LocalDate start, LocalDate end) {
        Booking booking = bookingRepo.findById(bookingId).orElseThrow();
        booking.setStartDate(start);
        booking.setEndDate(end);
        bookingRepo.saveAndFlush(booking);
    }

    private BookingStatus statusOf(Long bookingId) {
        return bookingRepo.findById(bookingId).orElseThrow().getStatus();
    }

    private void assertDate(Long dateId, int bookedCount, TourDateStatus status) {
        TourDate date = tourDateRepo.findById(dateId).orElseThrow();
        assertThat(date.getBookedCount()).isEqualTo(bookedCount);
        assertThat(date.getStatus()).isEqualTo(status);
    }
}
