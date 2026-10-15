package com.villagebooker.model;

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
@Table(name = "listing_history", indexes = {
        @Index(name = "idx_listing_history_listing", columnList = "listing_id")
})
@AllArgsConstructor
@NoArgsConstructor
public class ListingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_state", columnDefinition = "jsonb")
    private Map<String, Object> oldState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_state", columnDefinition = "jsonb")
    private Map<String, Object> newState;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "reason")
    private String reason;
}
