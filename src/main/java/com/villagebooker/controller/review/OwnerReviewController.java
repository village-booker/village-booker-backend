package com.villagebooker.controller.review;

import com.villagebooker.controller.common.Paging;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.review.ReviewReplyRequest;
import com.villagebooker.dto.review.ReviewResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.review.OwnerReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/reviews")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerReviewController {

    private final OwnerReviewService ownerReviewService;

    @GetMapping
    public PageResponse<ReviewResponse> findMine(@AuthenticationPrincipal AuthUser authUser,
                                                 @RequestParam(required = false) Long listingId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ownerReviewService.findMine(authUser.id(), listingId,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @PostMapping("/{id}/reply")
    public ReviewResponse reply(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id,
                                @Valid @RequestBody ReviewReplyRequest request) {
        return ownerReviewService.reply(authUser.id(), id, request);
    }
}
