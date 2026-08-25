package com.renaser.os.shared.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class FixedClock implements Clock {

    private final Instant instant;

    private FixedClock(Instant instant) {
        this.instant = instant;
    }

    public static FixedClock at(Instant instant) {
        return new FixedClock(instant);
    }

    @Override
    public Instant now() {
        return instant;
    }

    @Override
    public LocalDate today() {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
