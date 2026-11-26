package com.arcadiaeditions.orders.checkout.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Fixed test "now" (see the header of data.sql). */
public final class TestClock {

    public static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    public static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    private TestClock() {}
}
