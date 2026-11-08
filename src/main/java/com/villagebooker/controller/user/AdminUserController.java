package com.villagebooker.controller.user;

import com.villagebooker.controller.common.Paging;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.user.AdminUserResponse;
import com.villagebooker.dto.user.AdminUserSummaryResponse;
import com.villagebooker.dto.user.BlockUserRequest;
import com.villagebooker.enums.user.Role;
import com.villagebooker.service.user.AdminUserService;
import com.villagebooker.service.user.ProfileImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final ProfileImageService profileImageService;

    // newest first
    @GetMapping
    public PageResponse<AdminUserSummaryResponse> findAll(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) Role role,
                                                          @RequestParam(required = false) Boolean blocked,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return adminUserService.findAll(q, role, blocked, Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable Long id) {
        return adminUserService.get(id);
    }

    @PostMapping("/{id}/block")
    public AdminUserResponse block(@PathVariable Long id, @Valid @RequestBody BlockUserRequest request) {
        return adminUserService.block(id, request);
    }

    @PostMapping("/{id}/unblock")
    public AdminUserResponse unblock(@PathVariable Long id) {
        return adminUserService.unblock(id);
    }

    // an inappropriate profile picture (there is no review before it is shown)
    @DeleteMapping("/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable Long id) {
        profileImageService.delete(id);
    }
}
