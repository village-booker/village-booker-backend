package com.villagebooker.dto.user;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// "Postani domaćin": USER -> OWNER. Format and control digits of the account are checked in UserService.
public record BecomeHostRequest(
        @NotBlank @Size(max = 30) String bankAccount,
        @AssertTrue(message = "must accept the host terms") boolean acceptHostTerms
) {
}
