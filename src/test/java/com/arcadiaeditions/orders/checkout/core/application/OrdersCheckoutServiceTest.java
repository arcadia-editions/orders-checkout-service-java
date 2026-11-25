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
    void startOrderCheckoutStockUnavailableTest() {
        var input = new StartOrderCheckoutInput()
                .setCustomerId("CUST-100")
                .setCustomerEmail("customer100@example.com")
                .setPaymentMethodId("PM-100")
                .setCurrency("EUR")
                .addItems(new OrderItemInput().setSku("SKU-004").setQuantity(3).setUnitPrice(new BigDecimal("9.99")))
                .setShippingAddress(new ShippingAddressInput()
                        .setName("Ada Customer")
                        .setLine1("1 Main St")
                        .setCity("Madrid")
                        .setPostalCode("28001")
                        .setCountry("ES"));
        var before = orderRepository.count();

        var ex = assertThrows(StockUnavailableException.class, () -> ordersCheckoutService.startOrderCheckout(input));

        assertEquals("ORD-100", ex.getOrderId());
        assertEquals(1, ex.getUnavailableItems().size());
        assertEquals("SKU-004", ex.getUnavailableItems().get(0).sku());
        assertEquals(3, ex.getUnavailableItems().get(0).requestedQuantity());
        assertEquals(0, ex.getUnavailableItems().get(0).availableQuantity());
        assertEquals(before, orderRepository.count());
        assertTrue(orderRepository.findByOrderId("ORD-100").isEmpty());

        var producer = context.getEventsProducerInMemoryContext().ordersCheckoutEventsProducer();
        assertEquals(0, producer.getOnOrderCreatedCapturedMessages().size());
        assertEquals(1, producer.getOnStockUnavailableCapturedMessages().size());
        var event = (com.arcadiaeditions.orders.checkout.events.avro.StockUnavailable)
                producer.getOnStockUnavailableCapturedMessages().get(0).getPayload();
        assertEquals("ORD-100", event.getOrderId().toString());
        assertEquals("CUST-100", event.getCustomerId().toString());
        assertEquals("customer100@example.com", event.getCustomerEmail().toString());
        assertEquals(1, event.getUnavailableItems().size());
        assertEquals("SKU-004", event.getUnavailableItems().get(0).getSku().toString());
        assertEquals(3, event.getUnavailableItems().get(0).getRequestedQuantity());
        assertEquals(0, event.getUnavailableItems().get(0).getAvailableQuantity());
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
    void confirmOrderRejectedTest() {
        var producer = context.getEventsProducerInMemoryContext().ordersCheckoutEventsProducer();

        // C-05: ORD-005 CREATED, inventory answers NOT_HELD
        var notHeld = ordersCheckoutService.confirmOrder(new ConfirmOrderInput().setOrderId("ORD-005"));
        assertEquals(OrderStatus.CREATED, notHeld.getStatus());
        assertEquals(OrderStatus.CREATED, orderRepository.findByOrderId("ORD-005").orElseThrow().getStatus());
        assertEquals(0, producer.getOnOrderConfirmedCapturedMessages().size());
        assertEquals(1, producer.getOnOrderConfirmationRejectedCapturedMessages().size());
        var e1 = (com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmationRejected)
                producer.getOnOrderConfirmationRejectedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-005", e1.getOrderId().toString());
        assertEquals("CUST-005", e1.getCustomerId().toString());
        assertEquals("STOCK_NOT_HELD", e1.getReason().toString());
        assertEquals(TestClock.NOW.toString(), e1.getRejectedAt().toString());

        // C-06: ORD-006 CANCELLED; inventory must not be called (it would answer NOT_HELD)
        context.inventoryClient().notHeldOrderIds().add("ORD-006");
        var cancelled = ordersCheckoutService.confirmOrder(new ConfirmOrderInput().setOrderId("ORD-006"));
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(OrderStatus.CANCELLED, orderRepository.findByOrderId("ORD-006").orElseThrow().getStatus());
        assertEquals(0, producer.getOnOrderConfirmedCapturedMessages().size());
        assertEquals(2, producer.getOnOrderConfirmationRejectedCapturedMessages().size());
        var e2 = (com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmationRejected)
                producer.getOnOrderConfirmationRejectedCapturedMessages().get(1).getPayload();
        assertEquals("ORD-006", e2.getOrderId().toString());
        assertEquals("ORDER_CANCELLED", e2.getReason().toString());
        assertEquals(TestClock.NOW.toString(), e2.getRejectedAt().toString());
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
    void cancelOrderTest() { // C-03: ORD-003 CREATED -> CANCELLED
        var order = ordersCheckoutService.cancelOrder(
                new CancelOrderInput().setOrderId("ORD-003").setReason("PAYMENT_DECLINED"));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals("PAYMENT_DECLINED", order.getCancellationReason());
        assertEquals(TestClock.NOW, order.getCancelledAt());
        assertEquals(order, orderRepository.findByOrderId("ORD-003").orElseThrow());
        assertCancelledEvent("ORD-003", "PAYMENT_DECLINED");
    }

    @Test
    void cancelOrderFromConfirmedTest() { // C-04: ORD-004 CONFIRMED -> CANCELLED
        var order = ordersCheckoutService.cancelOrder(
                new CancelOrderInput().setOrderId("ORD-004").setReason("PAYMENT_VOIDED"));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals("PAYMENT_VOIDED", order.getCancellationReason());
        assertEquals(TestClock.NOW, order.getCancelledAt());
        assertCancelledEvent("ORD-004", "PAYMENT_VOIDED");
    }

    @Test
    void cancelOrderRedeliveryOnCancelledIsNoOp() { // C-07: ORD-007 already CANCELLED
        var before = orderRepository.findByOrderId("ORD-007").orElseThrow();
        var cancelledAt = before.getCancelledAt();

        var order = ordersCheckoutService.cancelOrder(
                new CancelOrderInput().setOrderId("ORD-007").setReason("RESERVATION_EXPIRED"));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals("PAYMENT_DECLINED", order.getCancellationReason());
        assertEquals(cancelledAt, order.getCancelledAt());
        var producer = context.getEventsProducerInMemoryContext().ordersCheckoutEventsProducer();
        assertEquals(0, producer.getOnOrderCancelledCapturedMessages().size());
    }

    private void assertCancelledEvent(String orderId, String reason) {
        var producer = context.getEventsProducerInMemoryContext().ordersCheckoutEventsProducer();
        assertEquals(1, producer.getOnOrderCancelledCapturedMessages().size());
        var event = (com.arcadiaeditions.orders.checkout.events.avro.OrderCancelled)
                producer.getOnOrderCancelledCapturedMessages().get(0).getPayload();
        assertEquals(orderId, event.getOrderId().toString());
        assertEquals(reason, event.getReason().toString());
        assertEquals(TestClock.NOW.toString(), event.getCancelledAt().toString());
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
