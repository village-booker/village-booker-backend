package com.villagebooker.service.listing;

import com.villagebooker.enums.listing.ImageStatus;
import com.villagebooker.model.Listing;
import com.villagebooker.repo.listing.ListingImageRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Main image of every listing on a page with one query (listing -> url; listings without an image are missing).
// Guests see only APPROVED images; the owner and the admin also the PENDING ones.
@Component
@RequiredArgsConstructor
public class MainImages {

    private static final Set<ImageStatus> PUBLIC = Set.of(ImageStatus.APPROVED);
    private static final Set<ImageStatus> ALL_VISIBLE = Set.of(ImageStatus.APPROVED, ImageStatus.PENDING);

    private final ListingImageRepo listingImageRepo;

    public Map<Long, String> forGuests(Collection<? extends Listing> listings) {
        return find(listings, PUBLIC);
    }

    public Map<Long, String> forOwnerOrAdmin(Collection<? extends Listing> listings) {
        return find(listings, ALL_VISIBLE);
    }

    private Map<Long, String> find(Collection<? extends Listing> listings, Set<ImageStatus> statuses) {
        if (listings.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = listings.stream().map(Listing::getId).collect(Collectors.toSet());
        return listingImageRepo.findMainImages(ids, statuses).stream()
                .collect(Collectors.toMap(ListingImageRepo.MainImage::getListingId, ListingImageRepo.MainImage::getUrl,
                        (first, second) -> first));
    }
}
