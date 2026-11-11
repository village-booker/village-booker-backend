package com.villagebooker.service.auth;

import com.villagebooker.config.TermsProperties;
import com.villagebooker.security.JwtService;
import com.villagebooker.dto.auth.AuthResponse;
import com.villagebooker.dto.auth.LoginRequest;
import com.villagebooker.dto.auth.RegisterRequest;
import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.ForbiddenException;
import com.villagebooker.enums.user.Language;
import com.villagebooker.enums.user.Role;
import com.villagebooker.model.User;
import com.villagebooker.repo.user.UserRepo;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TermsProperties terms;
    private final Clock clock;
    private final EmailVerificationService emailVerification;
    private final TransactionTemplate tx;

    // compared against when the email does not exist, so a failed login takes the same time either way
    // (otherwise response time reveals which emails are registered)
    private final String dummyPasswordHash;

    public AuthService(UserRepo userRepo, PasswordEncoder passwordEncoder, JwtService jwtService,
                       TermsProperties terms, Clock clock, EmailVerificationService emailVerification,
                       TransactionTemplate tx) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.terms = terms;
        this.clock = clock;
        this.emailVerification = emailVerification;
        this.tx = tx;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    // No @Transactional on register / login on purpose: BCrypt takes ~100 ms and must not hold a pooled
    // DB connection meanwhile (10 parallel logins would block the whole app). Each repo call runs in
    // its own short transaction; a single insert needs nothing more.
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepo.existsByEmail(email)) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email is already registered.");
        }
        checkPasswordLength(request.password());
        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(blankToNull(request.phone()));
        user.setRole(Role.USER);
        user.setLanguage(request.language() != null ? request.language() : Language.SR);
        user.setTermsVersion(terms.version());
        user.setTermsAcceptedAt(Instant.now(clock));

        // a concurrent registration with the same email fails on the unique constraint -> 409 (GlobalExceptionHandler);
        // the confirmation email is saved with the user and sent after the commit
        tx.executeWithoutResult(status -> {
            userRepo.save(user);
            emailVerification.sendLink(user);
        });
        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepo.findByEmail(normalizeEmail(request.email())).orElse(null);

        String hash = user != null ? user.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        // same answer for "no such email" and "wrong password"
        if (user == null || !passwordMatches) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        // only after the password check, so the answer does not tell anyone else that the account is blocked
        if (user.getBlockedAt() != null) {
            throw new ForbiddenException(ErrorCode.USER_BLOCKED, "This account is blocked.");
        }
        return toAuthResponse(user);
    }

    // @Size(max = 72) counts characters, BCrypt counts bytes (č, ž... take two)
    public static void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BadRequestException(ErrorCode.PASSWORD_TOO_LONG, "Password is too long.");
        }
    }

    private AuthResponse toAuthResponse(User user) {
        return AuthResponse.of(jwtService.issue(user), user);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
