package com.arcadiaeditions.orders.checkout.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Fixed clock for Spring tests on the {@code test} profile. Same instant as {@link TestClock#FIXED}. The in-memory
 * service tests do not boot Spring; {@link ServicesInMemoryConfig} passes that clock into the service itself.
 */
@Configuration
@Profile("test")
public class TestClockConfiguration {

    @Bean
    @Primary
    public Clock fixedClock() {
        return TestClock.FIXED;
    }
}
