package com.villagebooker.service.auth;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.ForbiddenException;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.config.EmailVerificationProperties;
import com.villagebooker.dto.auth.VerifyEmailRequest;
import com.villagebooker.model.User;
import com.villagebooker.repo.notification.EmailMessageRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.security.JwtService;
import com.villagebooker.service.notification.EmailOutbox;
import com.villagebooker.service.notification.NotificationMails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

// Email confirmation after registration. The link carries a signed token (JwtService.issueEmailVerification), so no
// table is needed: confirming twice changes nothing, and the token holds the email it was sent to.
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final JwtService jwtService;
    private final UserRepo userRepo;
    private final EmailMessageRepo emailRepo;
    private final EmailOutbox outbox;
    private final NotificationMails mails;
    private final EmailVerificationProperties properties;
    private final Clock clock;

    // in the caller's transaction (registration), sent after it commits
    public void sendLink(User user) {
        JwtService.IssuedToken token = jwtService.issueEmailVerification(user, properties.timeout());
        outbox.add(List.of(mails.emailVerification(user, properties.link() + token.token(), token.expiresAt())));
    }

    @Transactional
    public void verify(VerifyEmailRequest request) {
        JwtService.VerifiedEmail claims = jwtService.parseEmailVerification(request.token().trim())
                .orElseThrow(EmailVerificationService::invalidToken);
        User user = userRepo.findById(claims.userId())
                .filter(u -> u.getEmail().equals(claims.email()))
                .orElseThrow(EmailVerificationService::invalidToken);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now(clock));
        }
    }

    // a new link; at most one email to the user every minInterval, a request sooner is skipped silently
    @Transactional
    public void resend(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User " + userId + " not found."));
        if (user.getEmailVerifiedAt() != null) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_VERIFIED, "The email address is already confirmed.");
        }
        Instant since = Instant.now(clock).minus(properties.minInterval());
        if (emailRepo.existsByRecipientIdAndCreatedAtAfter(userId, since)) {
            return;
        }
        sendLink(user);
    }

    @Transactional(readOnly = true)
    public void requireVerified(Long userId) {
        if (!userRepo.existsByIdAndEmailVerifiedAtIsNotNull(userId)) {
            throw new ForbiddenException(ErrorCode.EMAIL_NOT_VERIFIED, "Confirm your email address first.");
        }
    }

    private static BadRequestException invalidToken() {
        return new BadRequestException(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID,
                "This confirmation link is invalid or has expired.");
    }
}
