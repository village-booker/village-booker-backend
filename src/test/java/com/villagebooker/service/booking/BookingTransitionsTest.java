package com.villagebooker.service.booking;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.enums.booking.BookingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.villagebooker.enums.booking.BookingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// the state machine of spec 3
class BookingTransitionsTest {

    @Test
    void flowsOfTheSpecAreAllowed() {
        // manual + deposit
        assertThat(BookingTransitions.isAllowed(AWAITING_OWNER_APPROVAL, AWAITING_PAYMENT)).isTrue();
        assertThat(BookingTransitions.isAllowed(AWAITING_PAYMENT, CONFIRMED)).isTrue();
        assertThat(BookingTransitions.isAllowed(CONFIRMED, COMPLETED)).isTrue();
        // manual without deposit
        assertThat(BookingTransitions.isAllowed(AWAITING_OWNER_APPROVAL, CONFIRMED)).isTrue();
        assertThat(BookingTransitions.isAllowed(AWAITING_OWNER_APPROVAL, REJECTED)).isTrue();
        // timeouts
        assertThat(BookingTransitions.isAllowed(AWAITING_OWNER_APPROVAL, EXPIRED)).isTrue();
        assertThat(BookingTransitions.isAllowed(AWAITING_PAYMENT, EXPIRED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_OWNER_APPROVAL", "AWAITING_PAYMENT", "CONFIRMED"})
    void everyActiveBookingCanBeCancelled(BookingStatus from) {
        assertThat(BookingTransitions.isAllowed(from, CANCELLED_BY_GUEST)).isTrue();
        assertThat(BookingTransitions.isAllowed(from, CANCELLED_BY_OWNER)).isTrue();
        assertThat(BookingTransitions.isAllowed(from, CANCELLED_MIN_NOT_REACHED)).isTrue();
    }

    @Test
    void shortcutsAndWayBackAreNotAllowed() {
        assertThat(BookingTransitions.isAllowed(AWAITING_PAYMENT, REJECTED)).isFalse();
        assertThat(BookingTransitions.isAllowed(AWAITING_PAYMENT, AWAITING_OWNER_APPROVAL)).isFalse();
        assertThat(BookingTransitions.isAllowed(CONFIRMED, EXPIRED)).isFalse();
        assertThat(BookingTransitions.isAllowed(CONFIRMED, REJECTED)).isFalse();
        assertThat(BookingTransitions.isAllowed(CONFIRMED, AWAITING_PAYMENT)).isFalse();
        assertThat(BookingTransitions.isAllowed(AWAITING_OWNER_APPROVAL, COMPLETED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(names = {"REJECTED", "EXPIRED", "CANCELLED_BY_GUEST", "CANCELLED_BY_OWNER",
            "CANCELLED_MIN_NOT_REACHED", "COMPLETED"})
    void finishedStatusesHaveNoWayOut(BookingStatus from) {
        assertThat(BookingStatus.FINISHED).contains(from);
        for (BookingStatus to : BookingStatus.values()) {
            assertThat(BookingTransitions.isAllowed(from, to)).as("%s -> %s", from, to).isFalse();
        }
    }

    @Test
    void forbiddenTransitionIs409WithBothStatuses() {
        assertThatThrownBy(() -> BookingTransitions.check(REJECTED, CONFIRMED))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> {
                    ConflictException ex = (ConflictException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.BOOKING_STATUS_INVALID);
                    assertThat(ex.getParams()).containsEntry("status", "REJECTED")
                            .containsEntry("targetStatus", "CONFIRMED");
                });
    }
}
