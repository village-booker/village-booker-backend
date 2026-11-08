package com.villagebooker.service.user;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.ForbiddenException;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.user.AdminUserResponse;
import com.villagebooker.dto.user.AdminUserSummaryResponse;
import com.villagebooker.dto.user.BlockUserRequest;
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

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepo userRepo;
    private final ListingRepo listingRepo;
    private final BookingRepo bookingRepo;
    private final Clock clock;

    // q: part of the email, first or last name
    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> findAll(String q, Role role, Boolean blocked, Pageable pageable) {
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
        if (blocked != null) {
            spec = spec.and((u, query, cb) -> blocked ? cb.isNotNull(u.get("blockedAt")) : cb.isNull(u.get("blockedAt")));
        }
        return PageResponse.of(userRepo.findAll(spec, pageable), AdminUserSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long userId) {
        return toResponse(find(userId));
    }

    // Takes effect at once: JwtAuthFilter checks every request. Listings and bookings of the user are not touched
    // (what happens to them is still open).
    @Transactional
    public AdminUserResponse block(Long userId, BlockUserRequest request) {
        User user = find(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenException(ErrorCode.CANNOT_BLOCK_ADMIN, "An admin account cannot be blocked.");
        }
        if (user.getBlockedAt() != null) {
            throw new ConflictException(ErrorCode.USER_ALREADY_BLOCKED, "User " + userId + " is already blocked.");
        }
        user.setBlockedAt(Instant.now(clock));
        user.setBlockReason(request.reason().trim());
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse unblock(Long userId) {
        User user = find(userId);
        if (user.getBlockedAt() == null) {
            throw new ConflictException(ErrorCode.USER_NOT_BLOCKED, "User " + userId + " is not blocked.");
        }
        user.setBlockedAt(null);
        user.setBlockReason(null);
        return toResponse(user);
    }

    private User find(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User " + userId + " not found."));
    }

    private AdminUserResponse toResponse(User user) {
        Long userId = user.getId();
        return AdminUserResponse.from(user,
                listingRepo.countByOwnerIdAndStatusNot(userId, ListingStatus.DELETED),
                bookingRepo.countByGuestId(userId),
                bookingRepo.countByListingOwnerIdAndStatus(userId, BookingStatus.CANCELLED_BY_OWNER));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
