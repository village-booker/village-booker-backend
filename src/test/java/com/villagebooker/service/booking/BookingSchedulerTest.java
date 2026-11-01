package com.villagebooker.service.booking;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingSchedulerTest {

    private final BookingJobs jobs = mock(BookingJobs.class);
    private final BookingScheduler scheduler = new BookingScheduler(jobs);

    // expiry first, so a tour date at its deadline counts only settled bookings
    @Test
    void stepsRunInOrder() {
        when(jobs.bookingsToExpire()).thenReturn(List.of(1L));
        when(jobs.datesPastMinimumDeadline()).thenReturn(List.of(2L));
        when(jobs.bookingsToComplete()).thenReturn(List.of(3L));
        when(jobs.datesToComplete()).thenReturn(List.of(4L));

        scheduler.run();

        InOrder order = inOrder(jobs);
        order.verify(jobs).expire(1L);
        order.verify(jobs).datesPastMinimumDeadline();
        order.verify(jobs).cancelBelowMinimum(2L);
        order.verify(jobs).complete(3L);
        order.verify(jobs).completeDate(4L);
    }

    @Test
    void oneFailingItemDoesNotStopTheOthers() {
        when(jobs.bookingsToExpire()).thenReturn(List.of(1L, 2L));
        when(jobs.datesPastMinimumDeadline()).thenReturn(List.of());
        when(jobs.bookingsToComplete()).thenReturn(List.of(3L));
        when(jobs.datesToComplete()).thenReturn(List.of());
        doThrow(new IllegalStateException("boom")).when(jobs).expire(1L);

        scheduler.run();

        InOrder order = inOrder(jobs);
        order.verify(jobs).expire(1L);
        order.verify(jobs).expire(2L);
        order.verify(jobs).complete(3L);
    }
}
