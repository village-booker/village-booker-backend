package com.villagebooker.service.user;

import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.user.AdminUserResponse;
import com.villagebooker.dto.user.AdminUserSummaryResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.user.Role;
import com.villagebooker.model.User;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.user.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepo userRepo;
    private final ListingRepo listingRepo;
    private final BookingRepo bookingRepo;

    // q: part of the email, first or last name
    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> findAll(String q, Role role, Pageable pageable) {
        Specification<User> spec = Specification.unrestricted();
        if (q != null && !q.isBlank()) {
            String like = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((u, query, cb) -> cb.or(
                    cb.like(u.get("email"), like, '\\'),
                    cb.like(cb.lower(u.get("firstName")), like, '\\'),
                    cb.like(cb.lower(u.get("lastName")), like, '\\')));
        }
        if (role != null) {
            spec = spec.and((u, query, cb) -> cb.equal(u.get("role"), role));
        }
        return PageResponse.of(userRepo.findAll(spec, pageable), AdminUserSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User " + userId + " not found."));
        return AdminUserResponse.from(user,
                listingRepo.countByOwnerIdAndStatusNot(userId, ListingStatus.DELETED),
                bookingRepo.countByGuestId(userId),
                bookingRepo.countByListingOwnerIdAndStatus(userId, BookingStatus.CANCELLED_BY_OWNER));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
