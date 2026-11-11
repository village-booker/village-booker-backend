package com.villagebooker.dto.notification;

import com.villagebooker.enums.notification.EmailStatus;
import com.villagebooker.model.EmailMessage;

import java.time.Instant;

// without the body: a password reset email holds a working link
public record AdminEmailResponse(Long id, Long recipientId, String recipientEmail, String subject, EmailStatus status,
                                 Instant createdAt, Instant sentAt) {

    public static AdminEmailResponse from(EmailMessage email) {
        return new AdminEmailResponse(email.getId(), email.getRecipient().getId(), email.getRecipient().getEmail(),
                email.getSubject(), email.getStatus(), email.getCreatedAt(), email.getSentAt());
    }
}
