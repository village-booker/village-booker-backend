package com.villagebooker.service.notification;

import com.villagebooker.model.EmailMessage;
import com.villagebooker.repo.notification.EmailMessageRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

// The one way emails enter email_message: saved in the caller's transaction (a rollback leaves no email) and
// sent on a background thread only after it commits (spec 14).
@Component
@RequiredArgsConstructor
public class EmailOutbox {

    private final EmailMessageRepo emailRepo;
    private final EmailDispatcher dispatcher;

    public void add(List<EmailMessage> emails) {
        if (emails.isEmpty()) {
            return;
        }
        List<Long> ids = emailRepo.saveAll(emails).stream().map(EmailMessage::getId).toList();
        // also when called from a BEFORE_COMMIT listener: afterCommit still runs it
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatcher.sendAll(ids);
            }
        });
    }
}
