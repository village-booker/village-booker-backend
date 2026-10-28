package com.villagebooker.repo.listing;

import com.villagebooker.enums.listing.ChangeRequestStatus;
import com.villagebooker.model.ListingChangeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingChangeRequestRepo extends JpaRepository<ListingChangeRequest, Long> {

    // admin queue, e.g. status = PENDING; listing and requester are mapped into the response
    @EntityGraph(attributePaths = {"listing", "requester"})
    Page<ListingChangeRequest> findByStatus(ChangeRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"listing", "requester"})
    Page<ListingChangeRequest> findByListingId(Long listingId, Pageable pageable);

    // at most one PENDING request per listing (checked under the listing lock)
    boolean existsByListingIdAndStatus(Long listingId, ChangeRequestStatus status);
}
