package com.arcadiaeditions.orders.checkout.infrastructure.jpa.inmemory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arcadiaeditions.orders.checkout.config.ServicesInMemoryConfig;
import com.arcadiaeditions.orders.checkout.core.domain.OrderStatus;
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * In-memory twin of {@code OrderRepository.findByOrderId}. Loads the same JSON fixtures the service tests use
 * (data/jpa/Order, mirrors of data.sql C-02 and C-06).
 */
class OrderRepositoryInMemoryTest {

    private final ServicesInMemoryConfig context = new ServicesInMemoryConfig();

    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        context.reloadTestData();
        orderRepository = context.orderRepository();
    }

    @Test
    void findByOrderIdReturnsTheFixtureOrEmpty() {
        // data/jpa/Order/2.json, C-02: ORD-002 CREATED, id 51
        var created = orderRepository.findByOrderId("ORD-002").orElseThrow();
        assertEquals("ORD-002", created.getOrderId());
        assertEquals(OrderStatus.CREATED, created.getStatus());
        assertEquals("CUST-002", created.getCustomerId());
        assertEquals(51L, created.getId());

        // data/jpa/Order/6.json, C-06: ORD-006 CANCELLED RESERVATION_EXPIRED, id 251
        var cancelled = orderRepository.findByOrderId("ORD-006").orElseThrow();
        assertEquals("ORD-006", cancelled.getOrderId());
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals("CUST-006", cancelled.getCustomerId());
        assertEquals("RESERVATION_EXPIRED", cancelled.getCancellationReason());
        assertEquals(251L, cancelled.getId());

        assertTrue(orderRepository.findByOrderId("ORD-UNKNOWN").isEmpty());
    }
}
