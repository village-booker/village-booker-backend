package com.villagebooker.service.booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

// Spec 8. Runs on every instance; that is safe because each step checks the state again (BookingJobs). ShedLock
// comes with Flyway. Order matters: requests expire before the minimum is counted, so a date at its deadline
// counts only settled bookings.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class BookingScheduler {

    private final BookingJobs jobs;

    @Scheduled(fixedDelayString = "${app.booking.scheduler-delay}")
    void run() {
        each("expire booking", jobs.bookingsToExpire(), jobs::expire);
        each("cancel tour date below minimum", jobs.datesPastMinimumDeadline(), jobs::cancelBelowMinimum);
        each("complete booking", jobs.bookingsToComplete(), jobs::complete);
        each("complete tour date", jobs.datesToComplete(), jobs::completeDate);
    }

    private static void each(String step, List<Long> ids, Consumer<Long> action) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (RuntimeException e) {
                log.warn("Scheduler could not {} {}; next run tries again", step, id, e);
            }
        }
    }
}
