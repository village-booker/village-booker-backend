package com.villagebooker.common.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AppTimeTest {

    @Test
    void todayUsesSerbianDateWhenUtcIsStillThePreviousDay() {
        // 12.10. 22:30 UTC = 13.10. 00:30 in Belgrade (CEST, UTC+2)
        Clock utcClock = Clock.fixed(Instant.parse("2026-10-12T22:30:00Z"), ZoneOffset.UTC);

        assertThat(AppTime.today(utcClock)).isEqualTo(LocalDate.of(2026, 10, 13));
    }

    @Test
    void startOfDayIsMidnightInBelgrade() {
        // summer time (UTC+2) and winter time (UTC+1)
        assertThat(AppTime.startOfDay(LocalDate.of(2026, 10, 13)))
                .isEqualTo(Instant.parse("2026-10-12T22:00:00Z"));
        assertThat(AppTime.startOfDay(LocalDate.of(2026, 12, 13)))
                .isEqualTo(Instant.parse("2026-12-12T23:00:00Z"));
    }
}
