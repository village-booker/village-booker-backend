package com.villagebooker.controller.payment;

import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.controller.booking.BookingTestSupport;
import com.villagebooker.controller.listing.ListingTestSupport;
import com.villagebooker.enums.payment.PaymentChannel;
import com.villagebooker.enums.payment.PaymentStatus;
import com.villagebooker.enums.payment.PaymentType;
import com.villagebooker.model.Booking;
import com.villagebooker.model.Payment;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.payment.PaymentRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.villagebooker.controller.booking.BookingTestSupport.OWNER_BOOKINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The deposit "paid" without a provider (app.payments.simulated=true): deposit listings from BookingTestSupport,
// 5000 per night, 30 % deposit.
// Runs against the configured database; @Transactional rolls every test back, nothing stays in the DB.
@SpringBootTest(properties = "app.payments.simulated=true")
@AutoConfigureMockMvc
@Transactional
class SimulatedPaymentFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private BookingRepo bookingRepo;

    @Autowired
    private PaymentRepo paymentRepo;

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
    void depositConfirmsTheBookingAndLowersTheAmountDue() throws Exception {
        Long listing = approved(support.create(owner, "/accommodations", BookingTestSupport.depositJson()));
        Long bookingId = bookings.book(guest, listing, d, d.plusDays(2), 2);

        pay(guest, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalPrice").value(10000))
                .andExpect(jsonPath("$.amountDue").value(7000))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());

        List<Payment> payments = paymentRepo.findByBookingId(bookingId);
        assertThat(payments).hasSize(1);
        Payment payment = payments.getFirst();
        assertThat(payment.getAmount()).isEqualByComparingTo("3000");
        assertThat(payment.getType()).isEqualTo(PaymentType.DEPOSIT);
        assertThat(payment.getChannel()).isEqualTo(PaymentChannel.ONLINE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getRefundAmount()).isEqualByComparingTo("0");

        pay(guest, bookingId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_STATUS_INVALID"));
        assertThat(paymentRepo.findByBookingId(bookingId)).hasSize(1);
    }

    @Test
    void fullPrepaymentIsOnePaymentOfTypeFull() throws Exception {
        Long listing = approved(support.create(owner, "/accommodations",
                BookingTestSupport.depositJson().replace("\"depositPercentage\": 30", "\"depositPercentage\": 100")));
        Long bookingId = bookings.book(guest, listing, d, d.plusDays(1), 2);

        pay(guest, bookingId).andExpect(jsonPath("$.amountDue").value(0));
        assertThat(paymentRepo.findByBookingId(bookingId).getFirst().getType()).isEqualTo(PaymentType.FULL);
    }

    @Test
    void manualApprovalComesBeforeThePayment() throws Exception {
        Long listing = approved(support.create(owner, "/accommodations",
                BookingTestSupport.manualApprovalWithDepositJson()));
        Long bookingId = bookings.book(guest, listing, d, d.plusDays(1), 2);

        pay(guest, bookingId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.params.status").value("AWAITING_OWNER_APPROVAL"));
        mvc.perform(post(OWNER_BOOKINGS + "/" + bookingId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.booking.status").value("AWAITING_PAYMENT"));
        pay(guest, bookingId).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void onlyTheGuestPaysAndOnlyBeforeTheDeadline() throws Exception {
        Long listing = approved(support.create(owner, "/accommodations", BookingTestSupport.depositJson()));
        Long bookingId = bookings.book(guest, listing, d, d.plusDays(1), 2);

        pay(support.userToken(), bookingId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));

        Booking booking = bookingRepo.findById(bookingId).orElseThrow();
        booking.setExpiresAt(Instant.now().minusSeconds(1));
        bookingRepo.saveAndFlush(booking);
        pay(guest, bookingId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_EXPIRED"));
        assertThat(paymentRepo.findByBookingId(bookingId)).isEmpty();
    }

    @Test
    void bookingWithoutDepositHasNothingToPay() throws Exception {
        Long cabin = approved(support.createAccommodation(owner));
        Long bookingId = bookings.book(guest, cabin, d, d.plusDays(1), 2);

        pay(guest, bookingId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.params.status").value("CONFIRMED"));
    }

    private Long approved(Long listingId) throws Exception {
        support.approveListing(admin, listingId).andExpect(status().isOk());
        return listingId;
    }

    private ResultActions pay(String token, Long bookingId) throws Exception {
        return mvc.perform(post(BookingTestSupport.BOOKINGS + "/" + bookingId + "/pay")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
