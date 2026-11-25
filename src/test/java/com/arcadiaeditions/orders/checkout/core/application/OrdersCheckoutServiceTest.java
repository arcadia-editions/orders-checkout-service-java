package com.arcadiaeditions.orders.checkout.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void confirmOrderTest() {
        var order = ordersCheckoutService.confirmOrder(new ConfirmOrderInput().setOrderId("ORD-002"));

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(TestClock.NOW, order.getConfirmedAt());
        assertEquals(order, orderRepository.findByOrderId("ORD-002").orElseThrow());

        var producer = context.getEventsProducerInMemoryContext().ordersCheckoutEventsProducer();
        assertEquals(1, producer.getOnOrderConfirmedCapturedMessages().size());
        var event = (com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmed)
                producer.getOnOrderConfirmedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-002", event.getOrderId().toString());
        assertEquals("CUST-002", event.getCustomerId().toString());
        assertEquals(TestClock.NOW.toString(), event.getConfirmedAt().toString());
        assertEquals(2, event.getItems().size());
        assertEquals("SKU-002", event.getItems().get(0).getSku().toString());
        assertEquals(2, event.getItems().get(0).getQuantity());
        assertEquals("2 Book Lane", event.getShippingAddress().getLine1().toString());
        assertEquals("ES", event.getShippingAddress().getCountry().toString());
    }

    @Test
    void confirmOrderRedeliveryOnConfirmedIsNoOp() {
        // inventory would answer NOT_HELD if it were called: redelivery must not call it
        context.inventoryClient().notHeldOrderIds().add("ORD-001");
        var before = orderRepository.findByOrderId("ORD-001").orElseThrow();
        var confirmedAt = before.getConfirmedAt();

        var order = ordersCheckoutService.confirmOrder(new ConfirmOrderInput().setOrderId("ORD-001"));

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(confirmedAt, order.getConfirmedAt());
        var producer = context.getEventsProducerInMemoryContext().ordersCheckoutEventsProducer();
        assertEquals(0, producer.getOnOrderConfirmedCapturedMessages().size());
    }

    @Test
    void confirmOrderUnknownOrderIdThrows() {
        assertThrows(
                java.util.NoSuchElementException.class,
                () -> ordersCheckoutService.confirmOrder(new ConfirmOrderInput().setOrderId("ORD-999")));
        var producer = context.getEventsProducerInMemoryContext().ordersCheckoutEventsProducer();
        assertEquals(0, producer.getOnOrderConfirmedCapturedMessages().size());
    }

    @Test
    void cancelOrderTest() { // TODO: implement this test
    }

    @Test
    void getOrderTest() {
        Long id = 1L; // fixtures: data/jpa/Order/1.json, C-01 ORD-001
        var order = ordersCheckoutService.getOrder(id);
        assertTrue(order.isPresent());
        assertEquals("ORD-001", order.get().getOrderId());
        assertEquals(OrderStatus.CONFIRMED, order.get().getStatus());
    }
}
