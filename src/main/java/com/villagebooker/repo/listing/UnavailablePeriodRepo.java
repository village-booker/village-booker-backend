package com.villagebooker.repo.listing;

import com.villagebooker.model.UnavailablePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UnavailablePeriodRepo extends JpaRepository<UnavailablePeriod, Long> {

    List<UnavailablePeriod> findByListingIdOrderByStartDate(Long listingId);

    List<UnavailablePeriod> findByListingIdAndEndDateGreaterThanEqualOrderByStartDate(Long listingId, LocalDate date);

    @Query("""
            select p from UnavailablePeriod p
            where p.listing.id = :listingId
              and p.startDate <= :to
              and :from <= p.endDate
            """)
    List<UnavailablePeriod> findOverlapping(@Param("listingId") Long listingId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    // closed intervals [start, end] overlap (spec 1.2)
    @Query("""
            select case when count(p) > 0 then true else false end
            from UnavailablePeriod p
            where p.listing.id = :listingId
              and p.startDate <= :endDate
              and :startDate <= p.endDate
            """)
    boolean existsOverlapping(@Param("listingId") Long listingId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);
}
