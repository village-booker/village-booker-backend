package com.villagebooker.dto.user;

import com.villagebooker.enums.Role;
import com.villagebooker.model.User;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String imageUrl,
        Role role,
        boolean host
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getImageUrl(),
                user.getRole(),
                user.getRole() == Role.OWNER);
    }
}
