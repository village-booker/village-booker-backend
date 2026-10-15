package com.villagebooker.repo;

import com.villagebooker.enums.ChangeRequestStatus;
import com.villagebooker.model.ListingChangeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingChangeRequestRepo extends JpaRepository<ListingChangeRequest, Long> {

    // admin queue, e.g. status = PENDING
    Page<ListingChangeRequest> findByStatus(ChangeRequestStatus status, Pageable pageable);

    List<ListingChangeRequest> findByListingIdOrderByCreatedAtDesc(Long listingId);

    Page<ListingChangeRequest> findByRequesterId(Long requesterId, Pageable pageable);
}
