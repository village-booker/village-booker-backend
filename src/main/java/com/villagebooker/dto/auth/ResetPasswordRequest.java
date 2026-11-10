package com.villagebooker.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 100) String token,
        // same rules as at registration; AuthService also checks the byte length
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {
}
