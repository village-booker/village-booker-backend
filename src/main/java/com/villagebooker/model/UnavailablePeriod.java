package com.villagebooker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "unavailable_period",
        indexes = {
                @Index(name = "idx_unavailable_period_listing", columnList = "listing_id, start_date")
        },
        check = {
                @CheckConstraint(name = "chk_unavailable_period_dates", constraint = "start_date <= end_date")
        })
@AllArgsConstructor
@NoArgsConstructor
public class UnavailablePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // closed interval [startDate, endDate]: every date is one blocked night
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;
}
