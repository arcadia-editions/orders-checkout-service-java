package com.arcadiaeditions.orders.checkout.adapters.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import com.arcadiaeditions.orders.checkout.adapters.events.PaymentAuthorizedConsumerService;
import com.arcadiaeditions.orders.checkout.asyncapi.provider.InMemoryOrdersCheckoutEventsProducer;
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository;
import com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmed;
import com.arcadiaeditions.orders.checkout.events.avro.OrderCreated;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;

/**
 * place-order-flow, orders side, completed path (ZFL 340-342): start-checkout 201 emits OrderCreated, PaymentAuthorized
 * confirms the order and emits OrderConfirmed, GET shows CONFIRMED. Needs Docker (PostgreSQL), like the other Spring
 * tests; events are captured by the in-memory producer of the test profile.
 */
class PlaceOrderFlowCompletedIntegrationTest extends BaseWebTestClientTest {

    @Autowired
    PaymentAuthorizedConsumerService paymentAuthorizedConsumer;

    @Autowired
    InMemoryOrdersCheckoutEventsProducer events;

    @Autowired
    OrderRepository orderRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void startCheckoutThenPaymentAuthorizedConfirmsTheOrder() {
        String requestBody = """
            {
              "customerId" : "CUS-100",
              "customerEmail" : "reader@example.com",
              "paymentMethodId" : "PM-100",
              "currency" : "EUR",
              "items" : [ { "sku" : "SKU-001", "quantity" : 2, "unitPrice" : 10.50 } ],
              "shippingAddress" : {
                "name" : "Reader One", "line1" : "1 Main St", "city" : "Madrid",
                "postalCode" : "28001", "country" : "ES"
              }
            }
        """;

        var body = webTestClient
                .method(POST)
                .uri("/api/orders/start-checkout")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isEqualTo(201)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("CREATED")
                .returnResult()
                .getResponseBody();
        var json = new String(body);
        String orderId = JsonPath.read(json, "$.orderId");
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();

        assertEquals(1, createdFor(orderId).size());
        assertEquals(0, confirmedFor(orderId).size());

        var payment = new PaymentAuthorized();
        payment.setOrderId(orderId);
        paymentAuthorizedConsumer.onPaymentAuthorizedEventV1(payment, null);

        assertEquals(1, confirmedFor(orderId).size());
        var confirmed = (OrderConfirmed) confirmedFor(orderId).get(0).getPayload();
        long createdVersion = ((OrderCreated) createdFor(orderId).get(0).getPayload()).getVersion();
        long confirmedVersion = confirmed.getVersion();
        assertTrue(
                confirmedVersion > createdVersion,
                "OrderConfirmed.version " + confirmedVersion + " must be after OrderCreated.version " + createdVersion);

        // the service already flushed the confirmation; a second flush would only write the spurious UPDATE of
        // the JSON value types (no equals) and bump the version again
        entityManager.clear();
        var persisted = orderRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(persisted.getVersion(), confirmed.getVersion());

        webTestClient
                .method(GET)
                .uri("/api/orders/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(200)
                .expectBody()
                .jsonPath("$.orderId")
                .isEqualTo(orderId)
                .jsonPath("$.status")
                .isEqualTo("CONFIRMED")
                .jsonPath("$.confirmedAt")
                .isNotEmpty();
    }

    private List<Message> createdFor(String orderId) {
        return events.getOnOrderCreatedCapturedMessages().stream()
                .filter(m -> orderId.equals(
                        ((OrderCreated) m.getPayload()).getOrderId().toString()))
                .toList();
    }

    private List<Message> confirmedFor(String orderId) {
        return events.getOnOrderConfirmedCapturedMessages().stream()
                .filter(m -> orderId.equals(
                        ((OrderConfirmed) m.getPayload()).getOrderId().toString()))
                .toList();
    }
}
