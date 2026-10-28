package com.villagebooker.enums.listing;

// PENDING: added to a public listing, waits for the admin, guests do not see it
// APPROVED: visible to guests (images of a listing that is not public yet are reviewed with the listing itself)
// REJECTED: removed from the storage; the row stays so the owner sees the reason
public enum ImageStatus {
    PENDING,
    APPROVED,
    REJECTED
}
