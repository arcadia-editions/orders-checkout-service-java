package com.arcadiaeditions.orders.checkout.adapters.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.arcadiaeditions.orders.checkout.config.ServicesInMemoryConfig;
import com.arcadiaeditions.orders.checkout.config.TestClock;
import com.arcadiaeditions.orders.checkout.core.domain.OrderStatus;
import com.arcadiaeditions.orders.checkout.core.inbound.OrdersCheckoutService;
import com.arcadiaeditions.orders.checkout.infrastructure.jpa.inmemory.OrderRepositoryInMemory;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PaymentAuthorized on the payment-authorized channel confirms the order (C-02).
 */
class PaymentAuthorizedConsumerServiceTest {

    ServicesInMemoryConfig context = new ServicesInMemoryConfig();

    OrdersCheckoutService ordersCheckoutService = context.ordersCheckoutService();

    PaymentAuthorizedConsumerService consumer = new PaymentAuthorizedConsumerService(ordersCheckoutService);

    OrderRepositoryInMemory orderRepository = context.orderRepository();

    @BeforeEach
    void setUp() {
        context.reloadTestData();
    }

    @Test
    void paymentAuthorizedForOrd002ConfirmsTheOrder() {
        // C-02 ORD-002 CREATED: data/jpa/Order/2.json (in-memory twin of data.sql)
        var payload = new PaymentAuthorized();
        payload.setOrderId("ORD-002");

        consumer.onPaymentAuthorizedEventV1(payload, null);

        var order = orderRepository.findByOrderId("ORD-002").orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(TestClock.NOW, order.getConfirmedAt());

        var producer = context.getEventsProducerInMemoryContext().ordersCheckoutEventsProducer();
        assertEquals(1, producer.getOnOrderConfirmedCapturedMessages().size());
        var event = (com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmed)
                producer.getOnOrderConfirmedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-002", event.getOrderId().toString());
    }
}
