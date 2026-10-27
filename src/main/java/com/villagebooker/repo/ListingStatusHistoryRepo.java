package com.villagebooker.repo;

import com.villagebooker.model.ListingStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingStatusHistoryRepo extends JpaRepository<ListingStatusHistory, Long> {

    Page<ListingStatusHistory> findByListingIdOrderByChangedAtDesc(Long listingId, Pageable pageable);
}
