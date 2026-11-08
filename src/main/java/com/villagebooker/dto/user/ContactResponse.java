package com.villagebooker.dto.user;

import com.villagebooker.model.User;

// a guest or a host with contact data, for the admin
public record ContactResponse(Long id, String firstName, String lastName, String email, String phone) {

    public static ContactResponse from(User user) {
        return new ContactResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getPhone());
    }
}
