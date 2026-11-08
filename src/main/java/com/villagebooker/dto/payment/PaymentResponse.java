package com.villagebooker.dto.payment;

import com.villagebooker.enums.payment.PaymentChannel;
import com.villagebooker.enums.payment.PaymentStatus;
import com.villagebooker.enums.payment.PaymentType;
import com.villagebooker.model.Payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        PaymentType type,
        PaymentChannel channel,
        PaymentStatus status,
        BigDecimal amount,
        BigDecimal refundAmount,
        Instant createdAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getType(), payment.getChannel(), payment.getStatus(),
                payment.getAmount(), payment.getRefundAmount(), payment.getCreatedAt());
    }
}
