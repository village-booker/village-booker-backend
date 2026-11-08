package com.villagebooker.dto.user;

import com.villagebooker.enums.user.Language;
import com.villagebooker.enums.user.Role;
import com.villagebooker.model.User;

import java.time.Instant;

// The bank account is left out on purpose: only the user sees it (UserResponse).
public record AdminUserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String imageUrl,
        Role role,
        Language language,
        Instant createdAt,
        String termsVersion,
        Instant termsAcceptedAt,
        String hostTermsVersion,
        Instant hostTermsAcceptedAt,
        // listings that are not DELETED
        long listingCount,
        // bookings made as a guest, in any status
        long bookingCount,
        // CANCELLED_BY_OWNER bookings of the host's listings, all time (spec 9.4)
        long ownerCancellationCount
) {

    public static AdminUserResponse from(User user, long listingCount, long bookingCount,
                                         long ownerCancellationCount) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getImageUrl(), user.getRole(), user.getLanguage(), user.getCreatedAt(),
                user.getTermsVersion(), user.getTermsAcceptedAt(), user.getHostTermsVersion(),
                user.getHostTermsAcceptedAt(), listingCount, bookingCount, ownerCancellationCount);
    }
}
