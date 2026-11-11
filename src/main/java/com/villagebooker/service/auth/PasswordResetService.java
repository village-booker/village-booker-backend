package com.villagebooker.service.auth;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.config.PasswordResetProperties;
import com.villagebooker.dto.auth.ForgotPasswordRequest;
import com.villagebooker.dto.auth.ResetPasswordRequest;
import com.villagebooker.model.PasswordResetToken;
import com.villagebooker.model.User;
import com.villagebooker.repo.auth.PasswordResetTokenRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.service.notification.EmailOutbox;
import com.villagebooker.service.notification.NotificationMails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

// "Forgot password": a one-time link by email (saved to email_message like every other email), valid for
// app.password-reset.timeout. Earlier tokens issued with a password change stay valid until they expire.
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetTokenRepo tokenRepo;
    private final UserRepo userRepo;
    private final EmailOutbox outbox;
    private final NotificationMails mails;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final PasswordResetProperties properties;
    private final Clock clock;

    // Silent for an unknown or blocked email, and for a repeated request within minInterval (no flood of emails);
    // the caller always gets 204.
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        User user = userRepo.findByEmail(AuthService.normalizeEmail(request.email())).orElse(null);
        if (user == null || user.getBlockedAt() != null) {
            return;
        }
        Instant now = Instant.now(clock);
        if (tokenRepo.existsByUserIdAndCreatedAtAfter(user.getId(), now.minus(properties.minInterval()))) {
            return;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken reset = new PasswordResetToken();
        reset.setUser(user);
        reset.setTokenHash(hash(token));
        reset.setCreatedAt(now);
        reset.setExpiresAt(now.plus(properties.timeout()));
        tokenRepo.save(reset);
        outbox.add(List.of(mails.passwordReset(user, properties.link() + token, reset.getExpiresAt())));
    }

    // BCrypt runs before the short transaction (see AuthService); an unknown, used or expired link -> 400.
    // Every other open link of the user is closed with it.
    public void reset(ResetPasswordRequest request) {
        AuthService.checkPasswordLength(request.newPassword());
        String passwordHash = passwordEncoder.encode(request.newPassword());
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            PasswordResetToken reset = tokenRepo.findByTokenHashForUpdate(hash(request.token().trim()))
                    .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                    .orElseThrow(() -> new BadRequestException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID,
                            "This password reset link is invalid or has expired."));
            reset.getUser().setPasswordHash(passwordHash);
            reset.setUsedAt(now);
            tokenRepo.closeUnused(reset.getUser().getId(), now);
        });
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
