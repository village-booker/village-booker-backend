package com.villagebooker.dto.listing;

import com.villagebooker.model.Listing;
import com.villagebooker.model.User;

public record AdminListingResponse(
        ListingResponse listing,
        Owner owner
) {

    public record Owner(Long id, String firstName, String lastName, String email, String phone) {
    }

    public static AdminListingResponse from(Listing listing) {
        User owner = listing.getOwner();
        return new AdminListingResponse(
                ListingResponse.from(listing),
                new Owner(owner.getId(), owner.getFirstName(), owner.getLastName(), owner.getEmail(), owner.getPhone()));
    }
}
