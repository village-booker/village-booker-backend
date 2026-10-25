package com.villagebooker.controller;

import com.villagebooker.service.UserService;
import com.villagebooker.security.AuthUser;
import com.villagebooker.dto.user.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // the logged-in user, fresh from the database (role, host flag...)
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser authUser) {
        return userService.getById(authUser.id());
    }
}
