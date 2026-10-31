package com.villagebooker.repo.tour;

import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.TourDate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TourDateRepo extends JpaRepository<TourDate, Long> {

    // serializes capacity checks (bookedCount) for one tour date (spec 5.1)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from TourDate d where d.id = :id")
    Optional<TourDate> findByIdForUpdate(@Param("id") Long id);

    @Query("select d.tour.id from TourDate d where d.id = :id")
    Optional<Long> findTourIdById(@Param("id") Long id);

    // one tour has at most one non-cancelled date per start day
    boolean existsByTourIdAndStartDateAndStatusNot(Long tourId, LocalDate startDate, TourDateStatus status);

    List<TourDate> findByTourIdAndStartDateGreaterThanEqualOrderByStartDate(Long tourId, LocalDate from);

    // upcoming dates a guest can book (statuses OPEN, CONFIRMED)
    List<TourDate> findByTourIdAndStatusInAndStartDateGreaterThanEqualOrderByStartDate(
            Long tourId, Collection<TourDateStatus> statuses, LocalDate from);

    // scheduler: dates whose minimum deadline has passed (cancelled if bookedCount < minGuests)
    List<TourDate> findByStatusAndMinGuestsDeadlineBefore(TourDateStatus status, Instant now);

    // scheduler: finished dates -> COMPLETED
    List<TourDate> findByStatusInAndEndDateBefore(Collection<TourDateStatus> statuses, LocalDate today);
}
