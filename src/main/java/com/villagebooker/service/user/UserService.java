package com.villagebooker.service.user;

import com.villagebooker.common.bank.BankAccountNumber;
import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.ForbiddenException;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.dto.auth.AuthResponse;
import com.villagebooker.dto.user.BecomeHostRequest;
import com.villagebooker.dto.user.ChangePasswordRequest;
import com.villagebooker.dto.user.UpdateBankAccountRequest;
import com.villagebooker.dto.user.UpdateProfileRequest;
import com.villagebooker.dto.user.UpdateLanguageRequest;
import com.villagebooker.dto.user.UserResponse;
import com.villagebooker.enums.user.Role;
import com.villagebooker.model.User;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import com.villagebooker.service.auth.EmailVerificationService;
import com.villagebooker.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepo userRepo;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final TermsProperties terms;
    private final Clock clock;
    private final EmailVerificationService emailVerification;

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional
    public AuthResponse becomeHost(Long userId, BecomeHostRequest request) {
        User user = findUser(userId);
        if (user.getRole() == Role.OWNER) {
            throw new ConflictException(ErrorCode.USER_ALREADY_HOST, "User is already a host.");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenException(ErrorCode.ADMIN_CANNOT_BE_HOST, "An admin account cannot become a host.");
        }
        emailVerification.requireVerified(userId);
        String bankAccount = normalizeBankAccount(request.bankAccount());

        user.setBankAccount(bankAccount);
        user.setHostTermsVersion(terms.hostVersion());
        user.setHostTermsAcceptedAt(Instant.now(clock));
        user.setRole(Role.OWNER);

        return AuthResponse.of(jwtService.issue(user), user);
    }

    // language the user picked in the app; kept so it follows them to other devices and is used for emails
    @Transactional
    public UserResponse updateLanguage(Long userId, UpdateLanguageRequest request) {
        User user = findUser(userId);
        user.setLanguage(request.language());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.setFirstName(request.firstName().strip());
        user.setLastName(request.lastName().strip());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().strip());
        return UserResponse.from(user);
    }

    // No @Transactional here and in updateBankAccount on purpose: BCrypt must not hold a pooled connection (see
    // AuthService). Tokens issued before the change stay valid until they expire, the filter does not go to the DB.
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        checkCurrentPassword(user, request.currentPassword());
        AuthService.checkPasswordLength(request.newPassword());
        String hash = passwordEncoder.encode(request.newPassword());
        tx.executeWithoutResult(status -> findUser(userId).setPasswordHash(hash));
    }

    public UserResponse updateBankAccount(Long userId, UpdateBankAccountRequest request) {
        User user = findUser(userId);
        // checked in the database, like in becomeHost
        if (user.getRole() != Role.OWNER) {
            throw new ForbiddenException(ErrorCode.NOT_A_HOST, "Only a host has a payout account.");
        }
        String bankAccount = normalizeBankAccount(request.bankAccount());
        checkCurrentPassword(user, request.currentPassword());
        return tx.execute(status -> {
            User saved = findUser(userId);
            saved.setBankAccount(bankAccount);
            return UserResponse.from(saved);
        });
    }

    private void checkCurrentPassword(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException(ErrorCode.CURRENT_PASSWORD_INVALID, "The current password is not correct.");
        }
    }

    private static String normalizeBankAccount(String bankAccount) {
        return BankAccountNumber.normalize(bankAccount)
                .orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_BANK_ACCOUNT, "Bank account number is not valid."));
    }

    private User findUser(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User " + id + " not found."));
    }
}
