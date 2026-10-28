package com.villagebooker.model;

import com.villagebooker.enums.listing.ImageStatus;

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
@Table(name = "listing_image", indexes = {
        @Index(name = "idx_listing_image_listing", columnList = "listing_id, sort_order")
})
@AllArgsConstructor
@NoArgsConstructor
public class ListingImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    // id of the file in the image storage, needed to delete it there
    @Column(name = "public_id", nullable = false)
    private String publicId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'APPROVED'")
    @Column(name = "status", nullable = false)
    private ImageStatus status = ImageStatus.APPROVED;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
