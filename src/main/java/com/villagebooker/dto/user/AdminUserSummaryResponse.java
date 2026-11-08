package com.villagebooker.dto.user;

import com.villagebooker.enums.user.Role;
import com.villagebooker.model.User;

import java.time.Instant;

public record AdminUserSummaryResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String imageUrl,
        Role role,
        Instant createdAt
) {

    public static AdminUserSummaryResponse from(User user) {
        return new AdminUserSummaryResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getImageUrl(), user.getRole(), user.getCreatedAt());
    }
}
