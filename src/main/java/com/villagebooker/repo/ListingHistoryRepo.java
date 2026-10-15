package com.villagebooker.repo;

import com.villagebooker.model.ListingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingHistoryRepo extends JpaRepository<ListingHistory, Long> {

    Page<ListingHistory> findByListingIdOrderByChangedAtDesc(Long listingId, Pageable pageable);
}
