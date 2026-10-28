package com.villagebooker.repo.listing;

import com.villagebooker.model.UnavailablePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UnavailablePeriodRepo extends JpaRepository<UnavailablePeriod, Long> {

    List<UnavailablePeriod> findByListingIdOrderByStartDate(Long listingId);

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
