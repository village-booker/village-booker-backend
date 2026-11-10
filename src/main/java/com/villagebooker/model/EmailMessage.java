package com.villagebooker.model;

import com.villagebooker.enums.notification.EmailStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

// Outbox for notification emails (spec 14): written in the same transaction as the change it reports, so a
// rollback leaves no email and a commit never loses one. No provider yet: rows stay PENDING; a sender added
// later sends them to recipient.email and marks them SENT / FAILED.
@Entity
@Getter
@Setter
@Table(name = "email_message",
        indexes = {
                @Index(name = "idx_email_message_status", columnList = "status, created_at"),
                @Index(name = "idx_email_message_recipient", columnList = "recipient_id")
        })
@AllArgsConstructor
@NoArgsConstructor
public class EmailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "body", nullable = false, length = 10000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmailStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
