package com.villagebooker.model;

import com.villagebooker.enums.user.Language;
import com.villagebooker.enums.user.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "app_user")
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "phone")
    private String phone;

    @Column(name = "image_url")
    private String imageUrl;

    // storage id of the profile picture, to delete the file when it is replaced or removed
    @Column(name = "image_public_id")
    private String imagePublicId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "bank_account")
    private String bankAccount;

    // USER < OWNER (host), ADMIN; see SecurityConfig.roleHierarchy
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    // language picked in the app (UI, emails); the default also fills existing rows when the column is added
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'SR'")
    @Column(name = "language", nullable = false, length = 2)
    private Language language = Language.SR;

    // general terms of use, accepted at registration (version = app.terms.version at that moment)
    @Column(name = "terms_version", nullable = false)
    private String termsVersion;

    @Column(name = "terms_accepted_at", nullable = false)
    private Instant termsAcceptedAt;

    // host terms, accepted when the user becomes OWNER; null = not a host
    @Column(name = "host_terms_version")
    private String hostTermsVersion;

    @Column(name = "host_terms_accepted_at")
    private Instant hostTermsAcceptedAt;

    // set by the admin; a blocked user can neither log in nor use an earlier token (JwtAuthFilter)
    @Column(name = "blocked_at")
    private Instant blockedAt;

    // seen only by admins
    @Column(name = "block_reason", length = 1000)
    private String blockReason;
}
