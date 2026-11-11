package com.villagebooker.controller.notification;

import com.villagebooker.controller.common.Paging;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.notification.AdminEmailResponse;
import com.villagebooker.enums.notification.EmailStatus;
import com.villagebooker.service.notification.AdminEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/emails")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminEmailController {

    private final AdminEmailService adminEmailService;

    // newest first
    @GetMapping
    public PageResponse<AdminEmailResponse> findAll(@RequestParam(required = false) EmailStatus status,
                                                    @RequestParam(required = false) Long recipientId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return adminEmailService.findAll(status, recipientId,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @PostMapping("/{id}/resend")
    public AdminEmailResponse resend(@PathVariable Long id) {
        return adminEmailService.resend(id);
    }
}
