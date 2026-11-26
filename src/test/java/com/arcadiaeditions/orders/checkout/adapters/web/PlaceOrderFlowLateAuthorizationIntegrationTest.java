package com.arcadiaeditions.orders.checkout.adapters.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.HttpMethod.GET;

import com.arcadiaeditions.orders.checkout.adapters.events.PaymentAuthorizedConsumerService;
import com.arcadiaeditions.orders.checkout.asyncapi.provider.InMemoryOrdersCheckoutEventsProducer;
import com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmationRejected;
import com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmed;
import com.arcadiaeditions.orders.checkout.infrastructure.inventory.InventoryClientStub;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;

/**
 * place-order-flow, orders side, late authorization (ZFL 147-164, P-01 A): PaymentAuthorized for an order whose stock is
 * no longer held, or that is already cancelled, emits one OrderConfirmationRejected and leaves the order unchanged.
 * Fixtures: C-05 ORD-005 (id 201, CREATED, inventory stub answers NOT_HELD), C-06 ORD-006 (id 251, CANCELLED
 * RESERVATION_EXPIRED).
 */
class PlaceOrderFlowLateAuthorizationIntegrationTest extends BaseWebTestClientTest {

    @Autowired
    PaymentAuthorizedConsumerService paymentAuthorizedConsumer;

    @Autowired
    InMemoryOrdersCheckoutEventsProducer events;

    @Autowired
    InventoryClientStub inventory;

    @BeforeEach
    void resetStub() {
        inventory.reset();
    }

    @Test
    void stockNotHeldRejectsConfirmationAndLeavesOrderCreated() {
        int rejectedBefore = rejectedFor("ORD-005").size();
        int confirmedBefore = confirmedFor("ORD-005").size();

        authorize("ORD-005");

        var rejected = rejectedFor("ORD-005");
        assertEquals(rejectedBefore + 1, rejected.size());
        var event =
                (OrderConfirmationRejected) rejected.get(rejected.size() - 1).getPayload();
        assertEquals("STOCK_NOT_HELD", event.getReason().toString());
        assertEquals(confirmedBefore, confirmedFor("ORD-005").size());
        assertEquals(List.of("ORD-005"), inventory.confirmStockReservationCalls());

        webTestClient
                .method(GET)
                .uri("/api/orders/{id}", 201)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(200)
                .expectBody()
                .jsonPath("$.orderId")
                .isEqualTo("ORD-005")
                .jsonPath("$.status")
                .isEqualTo("CREATED")
                .jsonPath("$.confirmedAt")
                .isEmpty();
    }

    @Test
    void cancelledOrderRejectsConfirmationWithoutCallingInventory() {
        int rejectedBefore = rejectedFor("ORD-006").size();
        int confirmedBefore = confirmedFor("ORD-006").size();

        authorize("ORD-006");

        var rejected = rejectedFor("ORD-006");
        assertEquals(rejectedBefore + 1, rejected.size());
        var event =
                (OrderConfirmationRejected) rejected.get(rejected.size() - 1).getPayload();
        assertEquals("ORDER_CANCELLED", event.getReason().toString());
        assertEquals(confirmedBefore, confirmedFor("ORD-006").size());
        assertFalse(inventory.confirmStockReservationCalls().contains("ORD-006"));

        webTestClient
                .method(GET)
                .uri("/api/orders/{id}", 251)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(200)
                .expectBody()
                .jsonPath("$.orderId")
                .isEqualTo("ORD-006")
                .jsonPath("$.status")
                .isEqualTo("CANCELLED")
                .jsonPath("$.cancellationReason")
                .isEqualTo("RESERVATION_EXPIRED")
                .jsonPath("$.confirmedAt")
                .isEmpty();
    }

    private void authorize(String orderId) {
        var payment = new PaymentAuthorized();
        payment.setOrderId(orderId);
        paymentAuthorizedConsumer.onPaymentAuthorizedEventV1(payment, null);
    }

    private List<Message> rejectedFor(String orderId) {
        return events.getOnOrderConfirmationRejectedCapturedMessages().stream()
                .filter(m -> orderId.equals(((OrderConfirmationRejected) m.getPayload())
                        .getOrderId()
                        .toString()))
                .toList();
    }

    private List<Message> confirmedFor(String orderId) {
        return events.getOnOrderConfirmedCapturedMessages().stream()
                .filter(m -> orderId.equals(
                        ((OrderConfirmed) m.getPayload()).getOrderId().toString()))
                .toList();
    }
}
