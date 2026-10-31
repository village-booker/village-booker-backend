package com.villagebooker.model;

import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.CancellationPolicy;
import com.villagebooker.enums.payment.PaymentMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@Setter
@Table(name = "booking",
        indexes = {
                @Index(name = "idx_booking_listing", columnList = "listing_id"),
                @Index(name = "idx_booking_guest", columnList = "guest_id"),
                @Index(name = "idx_booking_tour_date", columnList = "tour_date_id"),
                @Index(name = "idx_booking_status_expires", columnList = "status, expires_at")
        },
        check = {
                @CheckConstraint(name = "chk_booking_dates", constraint = "start_date <= end_date"),
                @CheckConstraint(name = "chk_booking_guest_count", constraint = "guest_count > 0"),
                @CheckConstraint(name = "chk_booking_total_price", constraint = "total_price >= 0"),
                @CheckConstraint(name = "chk_booking_amount_left", constraint = "amount_left BETWEEN 0 AND total_price"),
                @CheckConstraint(name = "chk_booking_refund_percentage", constraint = "refund_percentage BETWEEN 0 AND 100"),
                @CheckConstraint(name = "chk_booking_deposit_percentage", constraint = "deposit_percentage BETWEEN 0 AND 100")
        })
@AllArgsConstructor
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "total_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "amount_left", precision = 19, scale = 2, nullable = false)
    private BigDecimal amountDue;

    // closed interval [startDate, endDate]: accommodation = booked nights (check-out = endDate + 1), tour = tour days
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    // deadline for a status that can expire (AWAITING_OWNER_APPROVAL, AWAITING_PAYMENT); read by the scheduler
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "guest_count", nullable = false)
    private Integer guestCount;

    @Column(name = "note")
    private String note;

    // snapshot of the listing's terms at booking time
    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_policy", nullable = false)
    private CancellationPolicy cancellationPolicy;

    @Column(name = "free_cancellation_until")
    private Instant freeCancellationUntil;

    @Column(name = "refund_percentage", precision = 5, scale = 2)
    private BigDecimal refundPercentage;

    @Column(name = "deposit_percentage", precision = 5, scale = 2)
    private BigDecimal depositPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private User guest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_date_id")
    private TourDate tourDate;

    public long getDayCount() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    // spec 1: deposit flow only for online payment with a percentage above 0
    public boolean hasDeposit() {
        return paymentMethod != PaymentMethod.ON_SITE && depositPercentage != null && depositPercentage.signum() > 0;
    }

    // null when the booking has no deposit
    public BigDecimal getDepositAmount() {
        if (!hasDeposit()) {
            return null;
        }
        return totalPrice.multiply(depositPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
