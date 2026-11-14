package com.villagebooker.service.user;

import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.user.FavoriteResponse;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.model.Favorite;
import com.villagebooker.model.Listing;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.user.FavoriteRepo;
import com.villagebooker.service.listing.MainImages;
import com.villagebooker.service.listing.OwnerListingService;
import com.villagebooker.service.listing.PublicListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// The user's saved listings. Adding and removing are idempotent; only listings a guest can open (approved or
// paused) can be added and are listed, so a deleted or rejected listing quietly drops out of the list.
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepo favoriteRepo;
    private final ListingRepo listingRepo;
    private final MainImages mainImages;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<FavoriteResponse> findMine(Long userId, Pageable pageable) {
        Page<Favorite> page = favoriteRepo.findByUserIdAndListingStatusIn(userId, PublicListingService.VISIBLE, pageable);
        Map<Long, String> images = mainImages.forGuests(page.getContent().stream().map(Favorite::getListing).toList());
        return PageResponse.of(page, f -> new FavoriteResponse(
                PublicListingService.card(f.getListing(), images.get(f.getListing().getId())),
                PublicListingService.bookable(f.getListing()),
                f.getCreatedAt()));
    }

    // which of the given listings (e.g. the cards of one search page) the user has saved
    @Transactional(readOnly = true)
    public List<Long> findSaved(Long userId, Collection<Long> listingIds) {
        return listingIds.isEmpty() ? List.of() : favoriteRepo.findListingIds(userId, listingIds);
    }

    @Transactional
    public void add(Long userId, Long listingId) {
        Listing listing = listingRepo.findById(listingId)
                .filter(l -> PublicListingService.VISIBLE.contains(l.getStatus()))
                .orElseThrow(() -> OwnerListingService.listingNotFound(listingId));
        favoriteRepo.insertIfAbsent(userId, listing.getId(), Instant.now(clock));
    }

    @Transactional
    public void remove(Long userId, Long listingId) {
        favoriteRepo.deleteByUserIdAndListingId(userId, listingId);
    }
}
