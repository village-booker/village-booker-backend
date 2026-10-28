package com.villagebooker.model;

import com.villagebooker.enums.payment.PaymentChannel;
import com.villagebooker.enums.payment.PaymentStatus;
import com.villagebooker.enums.payment.PaymentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "payment",
        indexes = {
                @Index(name = "idx_payment_booking", columnList = "booking_id")
        },
        check = {
                @CheckConstraint(name = "chk_payment_amount", constraint = "amount > 0")
        })
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "stripe_payment_id", unique = true)
    private String stripePaymentId;

    @Column(name = "stripe_refund_id")
    private String stripeRefundId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private PaymentChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PaymentType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;
}
