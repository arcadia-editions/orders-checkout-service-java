package com.arcadiaeditions.orders.checkout.infrastructure.jpa;

import com.arcadiaeditions.orders.checkout.config.TestClock;
import com.arcadiaeditions.orders.checkout.core.domain.*;
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderRepositoryIntegrationTest extends BaseRepositoryIntegrationTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    OrderRepository orderRepository;

    @Test
    void findAllTest() {
        var results = orderRepository.findAll();
        Assertions.assertFalse(results.isEmpty());
    }

    @Test
    void findByIdTest() {
        var id = 1L;
        var order = orderRepository.findById(id).orElseThrow();
        Assertions.assertNotNull(order.getId());
        Assertions.assertNotNull(order.getVersion());
    }

    @Test
    void findByOrderIdTest() {
        // data.sql C-02: ORD-002 CREATED
        var created = orderRepository.findByOrderId("ORD-002").orElseThrow();
        Assertions.assertEquals("ORD-002", created.getOrderId());
        Assertions.assertEquals(OrderStatus.CREATED, created.getStatus());
        Assertions.assertEquals("CUST-002", created.getCustomerId());

        // data.sql C-06: ORD-006 CANCELLED RESERVATION_EXPIRED
        var cancelled = orderRepository.findByOrderId("ORD-006").orElseThrow();
        Assertions.assertEquals("ORD-006", cancelled.getOrderId());
        Assertions.assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        Assertions.assertEquals("CUST-006", cancelled.getCustomerId());
        Assertions.assertEquals("RESERVATION_EXPIRED", cancelled.getCancellationReason());

        Assertions.assertTrue(orderRepository.findByOrderId("ORD-UNKNOWN").isEmpty());
    }

    @Test
    void saveTest() {
        Order order = new Order();
        order.setOrderId("");
        order.setStatus(OrderStatus.values()[0]);
        order.setCustomerId("");
        order.setCustomerEmail("");
        order.setPaymentMethodId("");
        order.setItems(List.of(new OrderItem()));
        order.setShippingAddress(new ShippingAddress());
        order.setTotalAmount(BigDecimal.valueOf(0));
        order.setCurrency("");
        order.setCreatedAt(Instant.now());
        order.setConfirmedAt(Instant.now());
        order.setCancelledAt(Instant.now());
        order.setCancellationReason("");

        // Persist aggregate root
        var created = orderRepository.save(order);

        // reloading to get relationships persisted by id
        entityManager.flush();
        entityManager.refresh(created);
        Assertions.assertNotNull(created.getId());
        Assertions.assertNotNull(created.getVersion());
    }

    @Test
    void updateTest() {
        var id = 1L;
        var items = List.of(new OrderItem());
        var address = new ShippingAddress();
        var now = TestClock.NOW;
        var order = orderRepository.findById(id).orElseThrow();
        order.setOrderId("");
        order.setStatus(OrderStatus.values()[0]);
        order.setCustomerId("");
        order.setCustomerEmail("");
        order.setPaymentMethodId("");
        order.setItems(items);
        order.setShippingAddress(address);
        order.setTotalAmount(BigDecimal.valueOf(0));
        order.setCurrency("");
        order.setCreatedAt(now);
        order.setConfirmedAt(now);
        order.setCancelledAt(now);
        order.setCancellationReason("");

        order = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();
        order = orderRepository.findById(id).orElseThrow();
        Assertions.assertEquals("", order.getOrderId());
        Assertions.assertEquals(OrderStatus.values()[0], order.getStatus());
        Assertions.assertEquals("", order.getCustomerId());
        Assertions.assertEquals("", order.getCustomerEmail());
        Assertions.assertEquals("", order.getPaymentMethodId());
        // OrderItem has no equals; JSON reload is a new instance. Assert the collection, not identity.
        Assertions.assertEquals(items.size(), order.getItems().size());
        Assertions.assertNotNull(order.getShippingAddress());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.getTotalAmount()));
        Assertions.assertEquals("", order.getCurrency());
        Assertions.assertEquals(now, order.getCreatedAt());
        Assertions.assertEquals(now, order.getConfirmedAt());
        Assertions.assertEquals(now, order.getCancelledAt());
        Assertions.assertEquals("", order.getCancellationReason());
    }

    @Test
    void deleteTest() {
        var id = 1L;
        orderRepository.deleteById(id);
        var notFound = orderRepository.findById(id);
        Assertions.assertFalse(notFound.isPresent());
    }
}
