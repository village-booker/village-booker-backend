package com.villagebooker.controller.user;

import com.villagebooker.dto.auth.AuthResponse;
import com.villagebooker.dto.user.BecomeHostRequest;
import com.villagebooker.dto.user.ChangePasswordRequest;
import com.villagebooker.dto.user.UpdateBankAccountRequest;
import com.villagebooker.dto.user.UpdateProfileRequest;
import com.villagebooker.dto.user.UpdateLanguageRequest;
import com.villagebooker.dto.user.UserResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.auth.EmailVerificationService;
import com.villagebooker.service.user.ProfileImageService;
import com.villagebooker.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ProfileImageService profileImageService;
    private final EmailVerificationService emailVerificationService;

    // the logged-in user, fresh from the database (role, host flag...)
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser authUser) {
        return userService.getById(authUser.id());
    }

    // a new confirmation link (the first one is sent at registration)
    @PostMapping("/me/verification-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerificationEmail(@AuthenticationPrincipal AuthUser authUser) {
        emailVerificationService.resend(authUser.id());
    }

    // language switch (SR / EN); the frontend translates the UI and error codes, this only remembers the choice
    @PutMapping("/me/language")
    public UserResponse updateLanguage(@AuthenticationPrincipal AuthUser authUser,
                                       @Valid @RequestBody UpdateLanguageRequest request) {
        return userService.updateLanguage(authUser.id(), request);
    }

    // first name, last name, phone; the email stays as registered
    @PutMapping("/me")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthUser authUser,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(authUser.id(), request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser authUser,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authUser.id(), request);
    }

    @PutMapping("/me/bank-account")
    @PreAuthorize("hasRole('OWNER')")
    public UserResponse updateBankAccount(@AuthenticationPrincipal AuthUser authUser,
                                          @Valid @RequestBody UpdateBankAccountRequest request) {
        return userService.updateBankAccount(authUser.id(), request);
    }

    // multipart/form-data with one part "file" (JPEG, PNG or WebP, at most 5 MB); replaces the old picture
    @PutMapping(value = "/me/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadImage(@AuthenticationPrincipal AuthUser authUser,
                                    @RequestPart("file") MultipartFile file) {
        return profileImageService.upload(authUser.id(), file);
    }

    @DeleteMapping("/me/image")
    public UserResponse deleteImage(@AuthenticationPrincipal AuthUser authUser) {
        return profileImageService.delete(authUser.id());
    }

    @PostMapping("/me/become-host")
    public AuthResponse becomeHost(@AuthenticationPrincipal AuthUser authUser,
                                   @Valid @RequestBody BecomeHostRequest request) {
        return userService.becomeHost(authUser.id(), request);
    }
}
