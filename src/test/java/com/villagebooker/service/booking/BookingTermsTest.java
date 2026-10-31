package com.villagebooker.service.booking;

import com.villagebooker.common.time.AppTime;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.CancellationPolicy;
import com.villagebooker.enums.payment.PaymentMethod;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Booking;
import com.villagebooker.model.Listing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// snapshot of the terms (spec 1.1) and the starting status (spec 1, 3)
class BookingTermsTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 20);

    @Test
    void termsAreCopiedAndAmountDueIsTheWholePrice() {
        Listing listing = listing(CancellationPolicy.PARTIAL, 5, "40", PaymentMethod.COMBINED, "25");
        Booking booking = booking();

        BookingTerms.copy(listing, booking, new BigDecimal("12000.00"));

        assertThat(booking.getTotalPrice()).isEqualByComparingTo("12000");
        assertThat(booking.getAmountDue()).isEqualByComparingTo("12000");
        assertThat(booking.getCancellationPolicy()).isEqualTo(CancellationPolicy.PARTIAL);
        assertThat(booking.getRefundPercentage()).isEqualByComparingTo("40");
        assertThat(booking.getDepositPercentage()).isEqualByComparingTo("25");
        assertThat(booking.getPaymentMethod()).isEqualTo(PaymentMethod.COMBINED);
        // midnight in Belgrade 5 days before the start, which is 23:00 UTC the day before
        assertThat(booking.getFreeCancellationUntil()).isEqualTo(AppTime.startOfDay(START.minusDays(5)));
        assertThat(booking.getFreeCancellationUntil()).hasToString("2026-12-14T23:00:00Z");
    }

    @Test
    void noFreeCancellationWithoutDaysOrForNonRefundable() {
        assertThat(BookingTerms.freeCancellationUntil(
                listing(CancellationPolicy.NON_REFUNDABLE, 5, null, PaymentMethod.ON_SITE, null), START)).isNull();
        assertThat(BookingTerms.freeCancellationUntil(
                listing(CancellationPolicy.PARTIAL, null, "50", PaymentMethod.ON_SITE, null), START)).isNull();
        assertThat(BookingTerms.freeCancellationUntil(
                listing(CancellationPolicy.FREE, 0, null, PaymentMethod.ON_SITE, null), START))
                .isEqualTo(AppTime.startOfDay(START));
    }

    @Test
    void startingStatusFollowsApprovalAndDeposit() {
        Listing instant = listing(CancellationPolicy.FREE, 1, null, PaymentMethod.ON_SITE, null);
        assertThat(statusFor(instant)).isEqualTo(BookingStatus.CONFIRMED);

        Listing withDeposit = listing(CancellationPolicy.FREE, 1, null, PaymentMethod.ONLINE, "30");
        assertThat(statusFor(withDeposit)).isEqualTo(BookingStatus.AWAITING_PAYMENT);

        // 0 % is the same as no deposit
        Listing zeroDeposit = listing(CancellationPolicy.FREE, 1, null, PaymentMethod.ONLINE, "0");
        assertThat(statusFor(zeroDeposit)).isEqualTo(BookingStatus.CONFIRMED);

        withDeposit.setRequiresOwnerApproval(true);
        assertThat(statusFor(withDeposit)).isEqualTo(BookingStatus.AWAITING_OWNER_APPROVAL);
    }

    @Test
    void depositIsRoundedHalfUpAndOnlyForOnlinePayment() {
        Booking booking = booking();
        BookingTerms.copy(listing(CancellationPolicy.FREE, 1, null, PaymentMethod.COMBINED, "33.33"), booking,
                new BigDecimal("1000.15"));
        // 1000.15 * 33.33 % = 333.349995 -> 333.35
        assertThat(booking.getDepositAmount()).isEqualByComparingTo("333.35");

        Booking onSite = booking();
        onSite.setPaymentMethod(PaymentMethod.ON_SITE);
        onSite.setTotalPrice(new BigDecimal("1000.00"));
        onSite.setDepositPercentage(new BigDecimal("30"));
        assertThat(onSite.hasDeposit()).isFalse();
        assertThat(onSite.getDepositAmount()).isNull();
    }

    private static BookingStatus statusFor(Listing listing) {
        Booking booking = booking();
        BookingTerms.copy(listing, booking, new BigDecimal("1000.00"));
        return BookingTerms.initialStatus(listing, booking);
    }

    private static Booking booking() {
        Booking booking = new Booking();
        booking.setStartDate(START);
        booking.setEndDate(START.plusDays(2));
        return booking;
    }

    private static Listing listing(CancellationPolicy policy, Integer freeDays, String refund, PaymentMethod method,
                                   String deposit) {
        Listing listing = new Accommodation();
        listing.setCancellationPolicy(policy);
        listing.setFreeCancellationDays(freeDays);
        listing.setRefundPercentage(refund == null ? null : new BigDecimal(refund));
        listing.setPaymentMethod(method);
        listing.setDepositPercentage(deposit == null ? null : new BigDecimal(deposit));
        return listing;
    }
}
