package com.villagebooker.service.notification;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.notification.AdminEmailResponse;
import com.villagebooker.enums.notification.EmailStatus;
import com.villagebooker.model.EmailMessage;
import com.villagebooker.repo.notification.EmailMessageRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminEmailService {

    private final EmailMessageRepo emailRepo;
    private final EmailDispatcher dispatcher;

    @Transactional(readOnly = true)
    public PageResponse<AdminEmailResponse> findAll(EmailStatus status, Long recipientId, Pageable pageable) {
        Specification<EmailMessage> spec = Specification.unrestricted();
        if (status != null) {
            spec = spec.and((e, query, cb) -> cb.equal(e.get("status"), status));
        }
        if (recipientId != null) {
            spec = spec.and((e, query, cb) -> cb.equal(e.get("recipient").get("id"), recipientId));
        }
        return PageResponse.of(emailRepo.findAll(spec, pageable), AdminEmailResponse::from);
    }

    // FAILED, or PENDING when it was created while sending was off; sent right away, outside a transaction
    public AdminEmailResponse resend(Long id) {
        EmailMessage email = emailRepo.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.EMAIL_NOT_FOUND, "Email " + id + " not found."));
        if (email.getStatus() == EmailStatus.SENT) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_SENT, "Email " + id + " has already been sent.");
        }
        if (!dispatcher.enabled()) {
            throw new ConflictException(ErrorCode.EMAIL_SENDING_DISABLED, "Sending emails is not configured.");
        }
        dispatcher.send(id);
        return emailRepo.findWithRecipientById(id).map(AdminEmailResponse::from).orElseThrow();
    }
}
