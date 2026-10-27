package com.villagebooker.dto.listing;

import com.villagebooker.enums.ChangeRequestStatus;
import com.villagebooker.model.ListingChangeRequest;
import com.villagebooker.model.User;

import java.time.Instant;
import java.util.Map;

// A change request with old and new values side by side (values are text, see ListingFields).
// oldValues: PENDING -> current values on the listing (what will be replaced),
//            APPROVED -> values right before it was applied, REJECTED -> null (nothing was replaced).
public record ChangeRequestResponse(
        Long id,
        Long listingId,
        String listingName,
        ChangeRequestStatus status,
        Map<String, Object> requestedChanges,
        Map<String, Object> oldValues,
        String rejectionReason,
        Instant createdAt,
        Instant processedAt,
        Requester requester
) {

    public record Requester(Long id, String firstName, String lastName, String email) {
    }

    // oldValues is passed in, because for a PENDING request it is read from the listing
    public static ChangeRequestResponse from(ListingChangeRequest request, Map<String, Object> oldValues) {
        User requester = request.getRequester();
        return new ChangeRequestResponse(
                request.getId(),
                request.getListing().getId(),
                request.getListing().getName(),
                request.getStatus(),
                request.getRequestedChanges(),
                oldValues,
                request.getRejectionReason(),
                request.getCreatedAt(),
                request.getProcessedAt(),
                new Requester(requester.getId(), requester.getFirstName(), requester.getLastName(), requester.getEmail()));
    }
}
