package com.arcadiaeditions.orders.checkout.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.arcadiaeditions.orders.checkout.config.*;
import com.arcadiaeditions.orders.checkout.core.application.mappers.*;
import com.arcadiaeditions.orders.checkout.core.domain.*;
import com.arcadiaeditions.orders.checkout.core.inbound.*;
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*;
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.*;
import com.arcadiaeditions.orders.checkout.infrastructure.jpa.inmemory.*;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Acceptance Test for OrdersCheckoutService.
 */
class OrdersCheckoutServiceTest {

    private final Logger log = LoggerFactory.getLogger(getClass());

    ServicesInMemoryConfig context = new ServicesInMemoryConfig();
    OrdersCheckoutServiceImpl ordersCheckoutService = context.ordersCheckoutService();

    OrderRepositoryInMemory orderRepository = context.orderRepository();

    @BeforeEach
    void setUp() {
        context.reloadTestData();
    }

    @Test
    void startOrderCheckoutTest() { // TODO: implement this test
    }

    @Test
    void confirmOrderTest() { // TODO: implement this test
    }

    @Test
    void cancelOrderTest() { // TODO: implement this test
    }

    @Test
    void serviceSeesTheFixedTestInstant() {
        var clock = ordersCheckoutService.clock();
        assertEquals(TestClock.FIXED, clock);
        assertEquals(TestClock.NOW, clock.instant());
        assertEquals(Instant.parse("2026-10-14T10:00:00Z"), clock.instant());
        assertEquals(ZoneOffset.UTC, clock.getZone());
        assertEquals(TestClock.FIXED, new TestClockConfiguration().fixedClock());
    }

    @Test
    void systemClockBeanIsUtc() {
        var clock = new ClockConfiguration().clock();
        assertEquals(Clock.systemUTC(), clock);
        assertEquals(ZoneOffset.UTC, clock.getZone());

        try (var ctx = new AnnotationConfigApplicationContext(ClockConfiguration.class)) {
            assertEquals(Clock.systemUTC(), ctx.getBean(Clock.class));
        }
    }

    @Test
    void testProfilePrefersTheFixedClock() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().setActiveProfiles("test");
            ctx.register(ClockConfiguration.class, TestClockConfiguration.class);
            ctx.refresh();
            assertEquals(TestClock.FIXED, ctx.getBean(Clock.class));
        }
    }

    @Test
    void getOrderTest() {
        Long id = 1L; // fixtures: data/jpa/Order/1.json, C-01 ORD-001
        var order = ordersCheckoutService.getOrder(id);
        assertTrue(order.isPresent());
        org.junit.jupiter.api.Assertions.assertEquals("ORD-001", order.get().getOrderId());
        org.junit.jupiter.api.Assertions.assertEquals(
                OrderStatus.CONFIRMED, order.get().getStatus());
    }
}
