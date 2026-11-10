package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// One "forgot password" link. Only the SHA-256 of the token is stored: the token itself exists only in the link
// sent by email, so reading this table does not let anyone reset a password.
@Entity
@Getter
@Setter
@Table(name = "password_reset_token",
        uniqueConstraints = @UniqueConstraint(name = "uq_password_reset_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_password_reset_token_user", columnList = "user_id, created_at"))
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // set when the password is reset with it, or with another link of the same user
    @Column(name = "used_at")
    private Instant usedAt;
}
