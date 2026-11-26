package com.arcadiaeditions.orders.checkout.adapters.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.GET;

import com.arcadiaeditions.catalog.inventory.events.avro.StockReleaseReason;
import com.arcadiaeditions.catalog.inventory.events.avro.StockReleased;
import com.arcadiaeditions.orders.checkout.adapters.events.StockReleasedConsumerService;
import com.arcadiaeditions.orders.checkout.asyncapi.provider.InMemoryOrdersCheckoutEventsProducer;
import com.arcadiaeditions.orders.checkout.config.TestClock;
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository;
import com.arcadiaeditions.orders.checkout.events.avro.OrderCancelled;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;

/**
 * place-order-flow, orders side, cancelled path (ZFL 262-266, 340-344): StockReleased cancels the order, emits one
 * OrderCancelled with the reason, GET shows CANCELLED with the cancellation reason. Fixtures: C-03 ORD-003 (id 101,
 * CREATED), C-04 ORD-004 (id 151, CONFIRMED); the class is transactional so cases do not leak.
 */
class PlaceOrderFlowCancelledIntegrationTest extends BaseWebTestClientTest {

    @Autowired
    StockReleasedConsumerService stockReleasedConsumer;

    @Autowired
    InMemoryOrdersCheckoutEventsProducer events;

    @Autowired
    OrderRepository orderRepository;

    @PersistenceContext
    EntityManager entityManager;

    @ParameterizedTest(name = "{1} cancels {0} order")
    @CsvSource({
        "CREATED,PAYMENT_DECLINED,ORD-003,101",
        "CREATED,PAYMENT_RETRY_EXHAUSTED,ORD-003,101",
        "CREATED,RESERVATION_EXPIRED,ORD-003,101",
        "CONFIRMED,PAYMENT_VOIDED,ORD-004,151",
        "CONFIRMED,PAYMENT_VOID_FAILED,ORD-004,151"
    })
    void stockReleasedCancelsTheOrder(String from, String reason, String orderId, long id) {
        webTestClient
                .method(GET)
                .uri("/api/orders/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(200)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(from);
        int before = cancelledFor(orderId).size();

        var released = new StockReleased();
        released.setOrderId(orderId);
        released.setReason(StockReleaseReason.valueOf(reason));
        stockReleasedConsumer.onStockReleasedEventV1(released, null);

        var cancelled = cancelledFor(orderId);
        assertEquals(before + 1, cancelled.size());
        var event = (OrderCancelled) cancelled.get(cancelled.size() - 1).getPayload();
        assertEquals(reason, event.getReason().toString());

        // the service already flushed the cancellation; a second flush would only write the spurious UPDATE of
        // the JSON value types (no equals) and bump the version again
        entityManager.clear();
        var persisted = orderRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(persisted.getVersion(), event.getVersion());
        assertEquals(persisted.getCustomerId(), event.getCustomerId().toString());
        assertEquals(persisted.getCustomerEmail(), event.getCustomerEmail().toString());

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
                .isEqualTo("CANCELLED")
                .jsonPath("$.cancellationReason")
                .isEqualTo(reason)
                .jsonPath("$.cancelledAt")
                .isEqualTo(TestClock.NOW.toString());
    }

    private List<Message> cancelledFor(String orderId) {
        return events.getOnOrderCancelledCapturedMessages().stream()
                .filter(m -> orderId.equals(
                        ((OrderCancelled) m.getPayload()).getOrderId().toString()))
                .toList();
    }
}
