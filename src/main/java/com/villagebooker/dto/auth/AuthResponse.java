package com.villagebooker.dto.auth;

import com.villagebooker.dto.user.UserResponse;

import java.time.Instant;

// the client sends accessToken back as "Authorization: Bearer <accessToken>"
public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user
) {
}
