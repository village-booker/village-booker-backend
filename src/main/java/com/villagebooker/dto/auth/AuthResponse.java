package com.villagebooker.dto.auth;

import com.villagebooker.dto.user.UserResponse;
import com.villagebooker.model.User;
import com.villagebooker.security.JwtService;

import java.time.Instant;

// the client sends accessToken back as "Authorization: Bearer <accessToken>"
public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user
) {

    public static AuthResponse of(JwtService.IssuedToken token, User user) {
        return new AuthResponse(token.token(), "Bearer", token.expiresAt(), UserResponse.from(user));
    }
}
