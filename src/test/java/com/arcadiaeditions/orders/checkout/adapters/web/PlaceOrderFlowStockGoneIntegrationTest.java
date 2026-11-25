package com.arcadiaeditions.orders.checkout.adapters.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;

import com.arcadiaeditions.orders.checkout.asyncapi.provider.InMemoryOrdersCheckoutEventsProducer;
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository;
import com.arcadiaeditions.orders.checkout.events.avro.StockUnavailable;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;

/**
 * place-order-flow, orders side, stockGone path (ZFL 340-343): start-checkout with SKU-004 (unavailable in the
 * inventory stub) answers 409 with the StockUnavailable body, persists no Order and emits one StockUnavailable, no
 * OrderCreated. Needs Docker (PostgreSQL), like the other Spring tests.
 */
class PlaceOrderFlowStockGoneIntegrationTest extends BaseWebTestClientTest {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    InMemoryOrdersCheckoutEventsProducer events;

    @Test
    void startCheckoutWithUnavailableSkuAnswers409AndPersistsNothing() {
        String requestBody = """
            {
              "customerId" : "CUS-100",
              "customerEmail" : "reader@example.com",
              "paymentMethodId" : "PM-100",
              "currency" : "EUR",
              "items" : [ { "sku" : "SKU-004", "quantity" : 3, "unitPrice" : 10.50 } ],
              "shippingAddress" : {
                "name" : "Reader One", "line1" : "1 Main St", "city" : "Madrid",
                "postalCode" : "28001", "country" : "ES"
              }
            }
        """;
        long before = orderRepository.count();

        var body = webTestClient
                .method(POST)
                .uri("/api/orders/start-checkout")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.unavailableItems.length()")
                .isEqualTo(1)
                .jsonPath("$.unavailableItems[0].sku")
                .isEqualTo("SKU-004")
                .jsonPath("$.unavailableItems[0].requestedQuantity")
                .isEqualTo(3)
                .jsonPath("$.unavailableItems[0].availableQuantity")
                .isEqualTo(0)
                .returnResult()
                .getResponseBody();
        String orderId = JsonPath.read(new String(body), "$.orderId");

        assertTrue(orderRepository.findByOrderId(orderId).isEmpty());
        assertEquals(before, orderRepository.count());

        var unavailable = unavailableFor(orderId);
        assertEquals(1, unavailable.size());
        var event = (StockUnavailable) unavailable.get(0).getPayload();
        assertEquals("CUS-100", event.getCustomerId().toString());
        assertEquals(1, event.getUnavailableItems().size());
        assertEquals("SKU-004", event.getUnavailableItems().get(0).getSku().toString());
        assertEquals(3, event.getUnavailableItems().get(0).getRequestedQuantity());
        assertEquals(0, event.getUnavailableItems().get(0).getAvailableQuantity());

        assertEquals(
                0,
                events.getOnOrderCreatedCapturedMessages().stream()
                        .filter(m -> m.getPayload().toString().contains(orderId))
                        .count());
    }

    private List<Message> unavailableFor(String orderId) {
        return events.getOnStockUnavailableCapturedMessages().stream()
                .filter(m -> orderId.equals(
                        ((StockUnavailable) m.getPayload()).getOrderId().toString()))
                .toList();
    }
}
