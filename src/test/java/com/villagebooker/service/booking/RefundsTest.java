package com.villagebooker.service.booking;

import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.CancellationPolicy;
import com.villagebooker.model.Booking;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// refund table of spec 3
class RefundsTest {

    private static final Instant FREE_UNTIL = Instant.parse("2026-12-10T23:00:00Z");
    private static final Instant BEFORE = FREE_UNTIL.minusSeconds(1);
    private static final Instant AFTER = FREE_UNTIL.plusSeconds(1);

    @ParameterizedTest
    @EnumSource(CancellationPolicy.class)
    void hostAndMissingMinimumAlwaysRefundEverything(CancellationPolicy policy) {
        Booking booking = booking(policy, null, policy == CancellationPolicy.PARTIAL ? "40" : null);

        assertThat(Refunds.percentage(booking, BookingStatus.CANCELLED_BY_OWNER, AFTER)).isEqualByComparingTo("100");
        assertThat(Refunds.percentage(booking, BookingStatus.CANCELLED_MIN_NOT_REACHED, AFTER))
                .isEqualByComparingTo("100");
    }

    @Test
    void guestBeforeTheFreeDeadlineGetsEverything() {
        assertThat(guest(booking(CancellationPolicy.FREE, FREE_UNTIL, null), BEFORE)).isEqualByComparingTo("100");
        assertThat(guest(booking(CancellationPolicy.PARTIAL, FREE_UNTIL, "40"), BEFORE)).isEqualByComparingTo("100");
    }

    @Test
    void theFreeDeadlineItselfIsAlreadyLate() {
        assertThat(guest(booking(CancellationPolicy.FREE, FREE_UNTIL, null), FREE_UNTIL)).isEqualByComparingTo("0");
        assertThat(guest(booking(CancellationPolicy.PARTIAL, FREE_UNTIL, "40"), FREE_UNTIL)).isEqualByComparingTo("40");
    }

    @Test
    void guestAfterTheDeadlineDependsOnThePolicy() {
        assertThat(guest(booking(CancellationPolicy.FREE, FREE_UNTIL, null), AFTER)).isEqualByComparingTo("0");
        assertThat(guest(booking(CancellationPolicy.PARTIAL, FREE_UNTIL, "40"), AFTER)).isEqualByComparingTo("40");
        assertThat(guest(booking(CancellationPolicy.NON_REFUNDABLE, null, null), BEFORE)).isEqualByComparingTo("0");
    }

    @Test
    void partialWithoutFreeDaysIsAlwaysThePercentage() {
        assertThat(guest(booking(CancellationPolicy.PARTIAL, null, "25"), BEFORE)).isEqualByComparingTo("25");
    }

    @Test
    void amountIsRoundedHalfUpToCents() {
        assertThat(Refunds.amount(new BigDecimal("3000.00"), new BigDecimal("33.33"))).isEqualByComparingTo("999.90");
        // 617.285 -> 617.29
        assertThat(Refunds.amount(new BigDecimal("1234.57"), new BigDecimal("50"))).isEqualByComparingTo("617.29");
        assertThat(Refunds.amount(BigDecimal.ZERO, new BigDecimal("100"))).isEqualByComparingTo("0");
        assertThat(Refunds.amount(new BigDecimal("3000.00"), new BigDecimal("100")).scale()).isEqualTo(2);
    }

    private static BigDecimal guest(Booking booking, Instant now) {
        return Refunds.percentage(booking, BookingStatus.CANCELLED_BY_GUEST, now);
    }

    private static Booking booking(CancellationPolicy policy, Instant freeUntil, String refund) {
        Booking booking = new Booking();
        booking.setCancellationPolicy(policy);
        booking.setFreeCancellationUntil(freeUntil);
        booking.setRefundPercentage(refund == null ? null : new BigDecimal(refund));
        return booking;
    }
}
