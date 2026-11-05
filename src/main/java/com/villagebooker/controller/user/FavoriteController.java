package com.villagebooker.controller.user;

import com.villagebooker.controller.common.Paging;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.user.FavoriteResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.user.FavoriteService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    // last added first
    @GetMapping
    public PageResponse<FavoriteResponse> findMine(@AuthenticationPrincipal AuthUser authUser,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return favoriteService.findMine(authUser.id(),
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    // ?listingIds=1,2,3 -> the saved ones among them (hearts on a page of search results)
    @GetMapping("/ids")
    public List<Long> findSaved(@AuthenticationPrincipal AuthUser authUser,
                                @RequestParam @Size(max = Paging.MAX_PAGE_SIZE) List<Long> listingIds) {
        return favoriteService.findSaved(authUser.id(), listingIds);
    }

    @PutMapping("/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long listingId) {
        favoriteService.add(authUser.id(), listingId);
    }

    @DeleteMapping("/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long listingId) {
        favoriteService.remove(authUser.id(), listingId);
    }
}
