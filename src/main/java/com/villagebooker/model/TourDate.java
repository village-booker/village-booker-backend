package com.villagebooker.model;

import com.villagebooker.enums.TourDateStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "tour_date",
        indexes = {
                @Index(name = "idx_tour_date_tour_start", columnList = "tour_id, start_date")
        },
        check = {
                @CheckConstraint(name = "chk_tour_date_dates", constraint = "start_date <= end_date"),
                @CheckConstraint(name = "chk_tour_date_booked_count", constraint = "booked_count >= 0")
        })
@AllArgsConstructor
@NoArgsConstructor
public class TourDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // closed interval [startDate, endDate]: endDate = startDate + tour.durationDays - 1 (last day of the tour)
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "booked_count", nullable = false)
    private Integer bookedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TourDateStatus status = TourDateStatus.OPEN;

    // if bookedCount < tour.minGuests at this moment, the tour date is cancelled
    @Column(name = "min_guests_deadline", nullable = false)
    private Instant minGuestsDeadline;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_id", nullable = false)
    private Tour tour;
}
