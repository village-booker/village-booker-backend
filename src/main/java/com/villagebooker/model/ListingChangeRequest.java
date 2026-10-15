package com.villagebooker.model;

import com.villagebooker.enums.ChangeRequestStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Getter
@Setter
@Table(name = "listing_change_request", indexes = {
        @Index(name = "idx_change_request_listing", columnList = "listing_id"),
        @Index(name = "idx_change_request_requester", columnList = "requester_id"),
        @Index(name = "idx_change_request_status", columnList = "status")
})
@AllArgsConstructor
@NoArgsConstructor
public class ListingChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_changes", columnDefinition = "jsonb")
    private Map<String, Object> requestedChanges;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChangeRequestStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    // null until an admin processes the request
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private User admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;
}
