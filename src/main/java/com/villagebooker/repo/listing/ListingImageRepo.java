package com.villagebooker.repo.listing;

import com.villagebooker.enums.listing.ImageStatus;
import com.villagebooker.model.ListingImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ListingImageRepo extends JpaRepository<ListingImage, Long> {

    List<ListingImage> findByListingIdOrderBySortOrder(Long listingId);

    // main image (lowest sortOrder among the given statuses) of every listing on a page, in one query instead of N
    @Query("""
            select i.listing.id as listingId, i.url as url
            from ListingImage i
            where i.listing.id in :listingIds
              and i.status in :statuses
              and i.sortOrder = (select min(i2.sortOrder) from ListingImage i2
                                 where i2.listing.id = i.listing.id and i2.status in :statuses)
            """)
    List<MainImage> findMainImages(@Param("listingIds") Collection<Long> listingIds,
                                   @Param("statuses") Collection<ImageStatus> statuses);

    interface MainImage {
        Long getListingId();

        String getUrl();
    }

    // admin queue of images waiting for review; the listing is mapped into the response
    @EntityGraph(attributePaths = "listing")
    Page<ListingImage> findByStatus(ImageStatus status, Pageable pageable);
}
