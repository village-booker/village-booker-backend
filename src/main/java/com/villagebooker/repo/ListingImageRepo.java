package com.villagebooker.repo;

import com.villagebooker.model.ListingImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingImageRepo extends JpaRepository<ListingImage, Long> {

    List<ListingImage> findByListingIdOrderBySortOrder(Long listingId);
}
