package com.villagebooker.repo.listing;

import com.villagebooker.enums.listing.ImageStatus;
import com.villagebooker.model.ListingImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingImageRepo extends JpaRepository<ListingImage, Long> {

    List<ListingImage> findByListingIdOrderBySortOrder(Long listingId);

    // admin queue of images waiting for review; the listing is mapped into the response
    @EntityGraph(attributePaths = "listing")
    Page<ListingImage> findByStatus(ImageStatus status, Pageable pageable);
}
