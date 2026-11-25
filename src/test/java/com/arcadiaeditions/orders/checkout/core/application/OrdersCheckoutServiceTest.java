package com.arcadiaeditions.orders.checkout.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.arcadiaeditions.orders.checkout.config.*;
import com.arcadiaeditions.orders.checkout.core.application.mappers.*;
import com.arcadiaeditions.orders.checkout.core.domain.*;
import com.arcadiaeditions.orders.checkout.core.inbound.*;
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*;
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.*;
import com.arcadiaeditions.orders.checkout.infrastructure.jpa.inmemory.*;
import java.math.BigDecimal;
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
    void startOrderCheckoutTest() {
        var input = new StartOrderCheckoutInput()
                .setCustomerId("CUST-100")
                .setCustomerEmail("customer100@example.com")
                .setPaymentMethodId("PM-100")
                .setCurrency("EUR")
                .addItems(new OrderItemInput().setSku("SKU-002").setQuantity(2).setUnitPrice(new BigDecimal("19.99")))
                .addItems(new OrderItemInput().setSku("SKU-003").setQuantity(1).setUnitPrice(new BigDecimal("59.99")))
                .setShippingAddress(new ShippingAddressInput()
                        .setName("Ada Customer")
                        .setLine1("1 Main St")
                        .setCity("Madrid")
                        .setPostalCode("28001")
                        .setCountry("ES"));

        var order = ordersCheckoutService.startOrderCheckout(input);

        assertEquals("ORD-100", order.getOrderId());
        assertEquals(OrderStatus.CREATED, order.getStatus());
        assertEquals(0, new BigDecimal("99.97").compareTo(order.getTotalAmount()));
        assertEquals(TestClock.NOW, order.getCreatedAt());
        assertEquals(2, order.getItems().size());
        assertNotNull(order.getId());
        assertEquals(order, orderRepository.findByOrderId("ORD-100").orElseThrow());

        var producer = context.getEventsProducerInMemoryContext().ordersCheckoutEventsProducer();
        assertEquals(1, producer.getOnOrderCreatedCapturedMessages().size());
        assertEquals(0, producer.getOnStockUnavailableCapturedMessages().size());
        var event = (com.arcadiaeditions.orders.checkout.events.avro.OrderCreated)
                producer.getOnOrderCreatedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-100", event.getOrderId().toString());
        assertEquals(99.97, event.getAmount(), 0.0001);
        assertEquals("EUR", event.getCurrency().toString());
        assertNotNull(event.getVersion());
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
