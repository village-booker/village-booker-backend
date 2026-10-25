package com.villagebooker.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// no role field on purpose: registration always creates a USER
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt uses at most 72 bytes; AuthService also checks the byte length
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 30) String phone,
        @AssertTrue(message = "must accept the terms of use") boolean acceptTerms
) {
}
