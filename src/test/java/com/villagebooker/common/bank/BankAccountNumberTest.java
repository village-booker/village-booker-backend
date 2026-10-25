package com.villagebooker.common.bank;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BankAccountNumberTest {

    @Test
    void validNumberIsKeptInFullForm() {
        assertThat(BankAccountNumber.normalize("160-5100000123456-65")).contains("160-5100000123456-65");
    }

    @Test
    void shortenedMiddlePartIsPaddedWithZeros() {
        assertThat(BankAccountNumber.normalize("205-9011003562-09")).contains("205-0009011003562-09");
    }

    @Test
    void digitsWithoutDashesAndSpacesAreAccepted() {
        assertThat(BankAccountNumber.normalize("265104031000003550")).contains("265-1040310000035-50");
        assertThat(BankAccountNumber.normalize(" 265-1040310000035-50 ")).contains("265-1040310000035-50");
    }

    @Test
    void wrongControlDigitsAreRejected() {
        assertThat(BankAccountNumber.normalize("160-5100000123456-66")).isEmpty();
    }

    @Test
    void wrongFormatIsRejected() {
        assertThat(BankAccountNumber.normalize(null)).isEmpty();
        assertThat(BankAccountNumber.normalize("")).isEmpty();
        assertThat(BankAccountNumber.normalize("RS35160510000012345665")).isEmpty();
        assertThat(BankAccountNumber.normalize("16051000001234565")).isEmpty();
        assertThat(BankAccountNumber.normalize("160-51000001234567-65")).isEmpty();
    }
}
