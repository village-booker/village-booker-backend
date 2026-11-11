package com.villagebooker.service.notification;

import com.villagebooker.model.EmailMessage;
import com.villagebooker.repo.notification.EmailMessageRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.function.Supplier;

// Spec 14 with an outbox: the emails are saved just before the publishing transaction commits, so they see its
// final state (cancellation reason, refunds) and roll back with it; EmailOutbox sends them after the commit.
// Pending changes are flushed first, so a conflict (@Version) fails here with the same exception as it would at
// commit. A bug while preparing an email is only logged and never undoes the change; a database error is not caught,
// since the transaction is rolled back by it anyway.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationMails mails;
    private final EmailMessageRepo emailRepo;
    private final EmailOutbox outbox;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(BookingCreatedEvent event) {
        save(event, () -> mails.bookingCreated(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(BookingStatusChangedEvent event) {
        save(event, () -> mails.bookingStatusChanged(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(TourDateConfirmedEvent event) {
        save(event, () -> mails.tourDateConfirmed(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(ListingStatusChangedEvent event) {
        save(event, () -> mails.listingStatusChanged(event));
    }

    private void save(Object event, Supplier<List<EmailMessage>> build) {
        emailRepo.flush();
        List<EmailMessage> emails;
        try {
            emails = build.get();
        } catch (DataAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Could not prepare the emails for {}", event, e);
            return;
        }
        outbox.add(emails);
    }
}
