package com.arcadiaeditions.orders.checkout.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ClockConfigurationTest {

    @Test
    void testProfilePrefersTheFixedClock() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().setActiveProfiles("test");
            ctx.register(ClockConfiguration.class, TestClockConfiguration.class);
            ctx.refresh();
            assertEquals(TestClock.FIXED, ctx.getBean(Clock.class));
        }
    }
}
