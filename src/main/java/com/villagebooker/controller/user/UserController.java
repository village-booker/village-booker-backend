package com.villagebooker.controller.user;

import com.villagebooker.dto.auth.AuthResponse;
import com.villagebooker.dto.user.BecomeHostRequest;
import com.villagebooker.dto.user.UpdateLanguageRequest;
import com.villagebooker.dto.user.UserResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // language switch (SR / EN); the frontend translates the UI and error codes, this only remembers the choice
    @PutMapping("/me/language")
    public UserResponse updateLanguage(@AuthenticationPrincipal AuthUser authUser,
                                       @Valid @RequestBody UpdateLanguageRequest request) {
        return userService.updateLanguage(authUser.id(), request);
    }

    @PostMapping("/me/become-host")
    public AuthResponse becomeHost(@AuthenticationPrincipal AuthUser authUser,
                                   @Valid @RequestBody BecomeHostRequest request) {
        return userService.becomeHost(authUser.id(), request);
    }
}
