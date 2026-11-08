package com.villagebooker.dto.user;

import com.villagebooker.enums.user.Language;
import com.villagebooker.enums.user.Role;
import com.villagebooker.model.User;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String imageUrl,
        // only the user's own data (GET /me, login): the host's payout account
        String bankAccount,
        Role role,
        boolean host,
        Language language
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getImageUrl(),
                user.getBankAccount(),
                user.getRole(),
                user.getRole() == Role.OWNER,
                user.getLanguage());
    }
}
