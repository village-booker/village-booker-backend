package com.villagebooker.service.notification;

import com.villagebooker.config.MailProperties;
import com.villagebooker.enums.notification.EmailStatus;
import com.villagebooker.mail.EmailSendException;
import com.villagebooker.mail.EmailSender;
import com.villagebooker.model.EmailMessage;
import com.villagebooker.repo.notification.EmailMessageRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

// Sends emails from email_message right after the change that created them commits (EmailOutbox); no polling.
// Each email is read and marked in its own short transaction and sent outside of any. Any failure -> FAILED, and
// the admin sends it again (AdminEmailService.resend). Without app.mail.resend-api-key there is no EmailSender and
// emails stay PENDING.
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatcher {

    // Resend allows 2 requests a second
    private static final long PAUSE_MILLIS = 600;

    private final EmailMessageRepo emailRepo;
    private final ObjectProvider<EmailSender> sender;
    private final MailProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    private record Outgoing(String to, String subject, String text) {
    }

    public boolean enabled() {
        return sender.getIfAvailable() != null;
    }

    @Async
    public void sendAll(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                pause();
            }
            send(ids.get(i));
        }
    }

    // a SENT email is never sent again
    public void send(Long id) {
        EmailSender emailSender = sender.getIfAvailable();
        if (emailSender == null) {
            return;
        }
        Outgoing email = tx.execute(status -> emailRepo.findWithRecipientById(id)
                .filter(e -> e.getStatus() != EmailStatus.SENT)
                .map(this::outgoing)
                .orElse(null));
        if (email == null) {
            return;
        }
        try {
            emailSender.send(email.to(), email.subject(), email.text());
            mark(id, EmailStatus.SENT);
        } catch (EmailSendException e) {
            log.error("Email {} to {} was not sent: {}", id, email.to(), e.getMessage());
            mark(id, EmailStatus.FAILED);
        }
    }

    private Outgoing outgoing(EmailMessage email) {
        String recipient = email.getRecipient().getEmail();
        String redirect = properties.redirectTo();
        if (redirect == null || redirect.isBlank()) {
            return new Outgoing(recipient, email.getSubject(), email.getBody());
        }
        return new Outgoing(redirect, "[" + recipient + "] " + email.getSubject(), email.getBody());
    }

    private void mark(Long id, EmailStatus status) {
        tx.executeWithoutResult(s -> emailRepo.findById(id).ifPresent(e -> {
            e.setStatus(status);
            e.setSentAt(status == EmailStatus.SENT ? Instant.now(clock) : null);
        }));
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
