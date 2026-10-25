package com.villagebooker.model;

import com.villagebooker.enums.CancellationPolicy;
import com.villagebooker.enums.ListingStatus;
import com.villagebooker.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "listing",
        indexes = {
                @Index(name = "idx_listing_owner", columnList = "owner_id"),
                @Index(name = "idx_listing_district", columnList = "district"),
                @Index(name = "idx_listing_status", columnList = "status")
        },
        check = {
                @CheckConstraint(name = "chk_listing_guests", constraint = "min_guests > 0 AND min_guests <= max_guests"),
                @CheckConstraint(name = "chk_listing_deposit_percentage", constraint = "deposit_percentage BETWEEN 0 AND 100"),
                @CheckConstraint(name = "chk_listing_refund_percentage", constraint = "refund_percentage BETWEEN 0 AND 100"),
                @CheckConstraint(name = "chk_listing_free_cancellation_days", constraint = "free_cancellation_days >= 0")
        })
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "listing_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ListingStatus status;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "requires_owner_approval", nullable = false)
    private boolean requiresOwnerApproval = false;

    @Column(name = "max_guests", nullable = false)
    private Integer maxGuests;

    @Column(name = "min_guests", nullable = false)
    private Integer minGuests;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    @Column(name = "address")
    private String address;

    @Column(name = "place")
    private String place;

    @Column(name = "municipality")
    private String municipality;

    @Column(name = "district")
    private String district;

    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "latitude", precision = 9, scale = 6, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6, nullable = false)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_policy", nullable = false)
    private CancellationPolicy cancellationPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "deposit_percentage", precision = 5, scale = 2)
    private BigDecimal depositPercentage;

    // number of days before Booking.startDate until which cancellation is free
    @Column(name = "free_cancellation_days")
    private Integer freeCancellationDays;

    @Column(name = "refund_percentage", precision = 5, scale = 2)
    private BigDecimal refundPercentage;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "listing", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ListingImage> images;

    @OneToMany(mappedBy = "listing", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UnavailablePeriod> unavailablePeriods;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "listing_feature",
            joinColumns = @JoinColumn(name = "listing_id"),
            inverseJoinColumns = @JoinColumn(name = "feature_id")
    )
    private Set<Feature> features = new HashSet<>();
}
