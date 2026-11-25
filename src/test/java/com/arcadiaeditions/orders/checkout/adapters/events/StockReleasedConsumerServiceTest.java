package com.arcadiaeditions.orders.checkout.adapters.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.arcadiaeditions.catalog.inventory.events.avro.StockReleaseReason;
import com.arcadiaeditions.catalog.inventory.events.avro.StockReleased;
import com.arcadiaeditions.orders.checkout.config.ServicesInMemoryConfig;
import com.arcadiaeditions.orders.checkout.config.TestClock;
import com.arcadiaeditions.orders.checkout.core.domain.OrderStatus;
import com.arcadiaeditions.orders.checkout.core.inbound.OrdersCheckoutService;
import com.arcadiaeditions.orders.checkout.infrastructure.jpa.inmemory.OrderRepositoryInMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * StockReleased on the stock-released channel cancels the order (C-03).
 */
class StockReleasedConsumerServiceTest {

    ServicesInMemoryConfig context = new ServicesInMemoryConfig();

    OrdersCheckoutService ordersCheckoutService = context.ordersCheckoutService();

    StockReleasedConsumerService consumer = new StockReleasedConsumerService(ordersCheckoutService);

    OrderRepositoryInMemory orderRepository = context.orderRepository();

    @BeforeEach
    void setUp() {
        context.reloadTestData();
    }

    @Test
    void stockReleasedForOrd003CancelsTheOrder() {
        // C-03 ORD-003 CREATED: data/jpa/Order/3.json (in-memory twin of data.sql)
        var payload = new StockReleased();
        payload.setOrderId("ORD-003");
        payload.setReason(StockReleaseReason.PAYMENT_DECLINED);

        consumer.onStockReleasedEventV1(payload, null);

        var order = orderRepository.findByOrderId("ORD-003").orElseThrow();
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals("PAYMENT_DECLINED", order.getCancellationReason());
        assertEquals(TestClock.NOW, order.getCancelledAt());

        var producer = context.getEventsProducerInMemoryContext().ordersCheckoutEventsProducer();
        assertEquals(1, producer.getOnOrderCancelledCapturedMessages().size());
        var event = (com.arcadiaeditions.orders.checkout.events.avro.OrderCancelled)
                producer.getOnOrderCancelledCapturedMessages().get(0).getPayload();
        assertEquals("ORD-003", event.getOrderId().toString());
        assertEquals("PAYMENT_DECLINED", event.getReason().toString());
    }
}
