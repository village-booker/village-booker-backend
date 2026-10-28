package com.villagebooker.model;

import com.villagebooker.enums.listing.TourDifficulty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "tour", check = {
        @CheckConstraint(name = "chk_tour_duration_days", constraint = "duration_days >= 1"),
        @CheckConstraint(name = "chk_tour_price", constraint = "price_per_person > 0")
})
@AllArgsConstructor
@NoArgsConstructor
@DiscriminatorValue("TOUR")
@PrimaryKeyJoinColumn(name = "listing_id")
public class Tour extends Listing {

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    private TourDifficulty difficulty;

    @Column(name = "price_per_person", precision = 19, scale = 2, nullable = false)
    private BigDecimal pricePerPerson;
}
