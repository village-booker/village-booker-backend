package com.villagebooker.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

// The server runs in UTC; business dates (booking days, cancellation deadlines) are always Serbian local time.
// Never call LocalDate.now() / Instant.now() directly in services: use the injected Clock.
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Belgrade");

    private AppTime() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
