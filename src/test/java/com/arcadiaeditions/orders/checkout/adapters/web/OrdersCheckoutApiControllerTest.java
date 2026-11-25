package com.arcadiaeditions.orders.checkout.adapters.web;

import com.arcadiaeditions.orders.checkout.adapters.web.dtos.*;
import com.arcadiaeditions.orders.checkout.config.ServicesInMemoryConfig;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test controller for OrdersCheckoutApiController.
 */
public class OrdersCheckoutApiControllerTest {

    private final Logger log = LoggerFactory.getLogger(getClass());

    ServicesInMemoryConfig context = new ServicesInMemoryConfig();

    OrdersCheckoutApiController controller = new OrdersCheckoutApiController(context.ordersCheckoutService());

    @BeforeEach
    void setUp() {
        context.reloadTestData();
    }

    @Test
    public void startOrderCheckoutTest() {
        var item = new OrderItemInputDTO();
        item.setSku("SKU-002");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("19.99"));
        var address = new ShippingAddressInputDTO();
        address.setName("Ada Customer");
        address.setLine1("1 Main St");
        address.setCity("Madrid");
        address.setPostalCode("28001");
        address.setCountry("ES");
        var reqBody = new StartOrderCheckoutInputDTO();
        reqBody.setCustomerId("CUST-100");
        reqBody.setCustomerEmail("customer100@example.com");
        reqBody.setPaymentMethodId("PM-100");
        reqBody.setCurrency("EUR");
        reqBody.setItems(List.of(item));
        reqBody.setShippingAddress(address);

        var response = controller.startOrderCheckout(reqBody);

        Assertions.assertEquals(201, response.getStatusCode().value());
        var body = response.getBody();
        Assertions.assertEquals("ORD-100", body.getOrderId());
        Assertions.assertEquals(OrderStatusDTO.CREATED, body.getStatus());
        Assertions.assertEquals(0, new BigDecimal("39.98").compareTo(body.getTotalAmount()));
        Assertions.assertEquals(
                OffsetDateTime.ofInstant(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC), body.getCreatedAt());
    }

    @Test
    public void getOrderTest() {
        Long id = 1L; // fixtures: data/jpa/Order/1.json, C-01 ORD-001
        var response = controller.getOrder(id);
        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals("ORD-001", response.getBody().getOrderId());
    }
}
