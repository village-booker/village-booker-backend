package com.villagebooker.service.listing;

import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.model.Listing;
import com.villagebooker.model.ListingStatusHistory;
import com.villagebooker.repo.listing.ListingStatusHistoryRepo;
import com.villagebooker.repo.user.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListingStatusHistoryWriter {

    private final ListingStatusHistoryRepo historyRepo;
    private final UserRepo userRepo;

    // changedById = the admin / owner who did it, null for the system
    public void statusChanged(Listing listing, ListingStatus from, ListingStatus to, Long changedById, String reason) {
        ListingStatusHistory history = new ListingStatusHistory();
        history.setListing(listing);
        history.setOldStatus(from);
        history.setNewStatus(to);
        history.setChangedBy(changedById == null ? null : userRepo.getReferenceById(changedById));
        history.setReason(reason);
        historyRepo.save(history);
    }
}
