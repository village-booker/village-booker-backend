package com.villagebooker.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// the current password too: a stolen token alone must not be able to redirect the host's payouts
public record UpdateBankAccountRequest(
        @NotBlank @Size(max = 30) String bankAccount,
        @NotBlank @Size(max = 72) String currentPassword
) {
}
