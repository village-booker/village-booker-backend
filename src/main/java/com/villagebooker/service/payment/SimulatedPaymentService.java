package com.villagebooker.service.payment;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.dto.booking.BookingResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.payment.PaymentChannel;
import com.villagebooker.enums.payment.PaymentStatus;
import com.villagebooker.enums.payment.PaymentType;
import com.villagebooker.model.Booking;
import com.villagebooker.model.Payment;
import com.villagebooker.repo.payment.PaymentRepo;
import com.villagebooker.service.booking.BookingService;
import com.villagebooker.service.booking.BookingStatusChanger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

// Stand-in for the online payment until a provider is chosen (open decision): the deposit is "paid" at once and
// the booking confirmed. Exists only with app.payments.simulated=true, never in production.
@Service
@ConditionalOnProperty(name = "app.payments.simulated", havingValue = "true")
@RequiredArgsConstructor
public class SimulatedPaymentService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BookingService bookingService;
    private final BookingStatusChanger statusChanger;
    private final PaymentRepo paymentRepo;

    @Transactional
    public BookingResponse payDeposit(Long guestId, Long bookingId) {
        Booking booking = bookingService.findOwn(guestId, bookingId);
        if (booking.getStatus() != BookingStatus.AWAITING_PAYMENT) {
            throw new ConflictException(ErrorCode.BOOKING_STATUS_INVALID,
                    "Booking " + bookingId + " is not waiting for a payment.",
                    Map.of("status", booking.getStatus().name(), "expectedStatus", BookingStatus.AWAITING_PAYMENT.name()));
        }
        statusChanger.requireNotExpired(booking);

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getDepositAmount());
        payment.setChannel(PaymentChannel.ONLINE);
        payment.setType(booking.getDepositPercentage().compareTo(HUNDRED) == 0 ? PaymentType.FULL : PaymentType.DEPOSIT);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepo.save(payment);

        booking.setAmountDue(booking.getAmountDue().subtract(payment.getAmount()));
        statusChanger.moveTo(booking, BookingStatus.CONFIRMED);
        return BookingResponse.from(booking);
    }
}
