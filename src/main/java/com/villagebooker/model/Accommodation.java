package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "accommodation", check = {
        @CheckConstraint(name = "chk_accommodation_base_price", constraint = "base_price_per_night > 0"),
        @CheckConstraint(name = "chk_accommodation_extra_guest_price", constraint = "extra_guest_price >= 0"),
        @CheckConstraint(name = "chk_accommodation_base_guests", constraint = "base_guests >= 1"),
        @CheckConstraint(name = "chk_accommodation_beds", constraint = "double_bed_count >= 0 AND single_bed_count >= 0"),
        @CheckConstraint(name = "chk_accommodation_room_count", constraint = "room_count >= 1")
})
@AllArgsConstructor
@NoArgsConstructor
@DiscriminatorValue("ACCOMMODATION")
@PrimaryKeyJoinColumn(name = "listing_id")
public class Accommodation extends Listing {

    @Column(name = "base_price_per_night", precision = 19, scale = 2, nullable = false)
    private BigDecimal basePricePerNight;

    @Column(name = "base_guests", nullable = false)
    private Integer baseGuests;

    @Column(name = "extra_guest_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal extraGuestPrice;

    @Column(name = "double_bed_count", nullable = false)
    private Integer doubleBedCount;

    @Column(name = "single_bed_count", nullable = false)
    private Integer singleBedCount;

    @Column(name = "room_count", nullable = false)
    private Integer roomCount;
}
