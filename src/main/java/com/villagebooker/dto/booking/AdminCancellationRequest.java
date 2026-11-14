package com.villagebooker.dto.booking;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// the admin decides how much of what was paid goes back; the cancellation policy of the booking does not apply
public record AdminCancellationRequest(
        @NotBlank @Size(max = 255) String reason,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal refundPercentage) {
}
