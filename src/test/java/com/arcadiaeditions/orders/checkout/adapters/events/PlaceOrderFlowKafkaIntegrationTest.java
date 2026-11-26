package com.arcadiaeditions.orders.checkout.adapters.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import com.arcadiaeditions.catalog.inventory.events.avro.StockReleaseReason;
import com.arcadiaeditions.catalog.inventory.events.avro.StockReleased;
import com.arcadiaeditions.orders.checkout.asyncapi.provider.OrdersCheckoutEventsProducer;
import com.arcadiaeditions.orders.checkout.config.DockerComposeInitializer;
import com.arcadiaeditions.orders.checkout.config.TestClock;
import com.arcadiaeditions.orders.checkout.core.domain.OrderStatus;
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository;
import com.arcadiaeditions.orders.checkout.events.avro.OrderCancelled;
import com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmationRejected;
import com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmed;
import com.arcadiaeditions.orders.checkout.events.avro.OrderCreated;
import com.arcadiaeditions.orders.checkout.events.avro.StockUnavailable;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.schema.registry.avro.AvroSchemaRegistryClientMessageConverter;
import org.springframework.http.MediaType;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

/**
 * place-order-flow over Kafka. S1 (ZFL 91, 155; ZDL 136): start-checkout publishes OrderCreated, PaymentAuthorized
 * reaches confirmOrder through payment-authorized-event-v1-in-0, and OrderConfirmed lands on its topic. S3 (ZFL
 * 306-308; ZDL 142): StockReleased reaches cancelOrder through stock-released-event-v1-in-0 (started here, same as
 * the payment binding) and OrderCancelled lands on its topic. S2 (ZFL 92): start-checkout with SKU-004 publishes
 * StockUnavailable. S4 (ZFL 162-163): PaymentAuthorized for a cancelled order publishes OrderConfirmationRejected.
 * Real producer (StreamBridge), Avro payloads through the compose Schema Registry. Not transactional: the binder
 * thread must see the committed order.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "arcadia.test.events-producer=kafka",
            "spring.cloud.stream.bindings.payment-authorized-event-v1-in-0.consumer.auto-startup=true",
            "spring.cloud.stream.bindings.stock-released-event-v1-in-0.consumer.auto-startup=true"
        })
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
class PlaceOrderFlowKafkaIntegrationTest {

    static final String ORDER_CREATED_TOPIC = "orders-checkout.order-created.event.avro.v1";
    static final String ORDER_CONFIRMED_TOPIC = "orders-checkout.order-confirmed.event.avro.v1";
    static final String ORDER_CANCELLED_TOPIC = "orders-checkout.order-cancelled.event.avro.v1";
    static final String STOCK_UNAVAILABLE_TOPIC = "orders-checkout.stock-unavailable.event.avro.v1";
    static final String ORDER_CONFIRMATION_REJECTED_TOPIC = "orders-checkout.order-confirmation-rejected.event.avro.v1";
    static final String PAYMENT_AUTHORIZED_TOPIC = "payments-processing.payment-authorized.event.avro.v1";
    static final String STOCK_RELEASED_TOPIC = "catalog-inventory.stock-released.event.avro.v1";
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    WebApplicationContext context;

    @Autowired
    OrdersCheckoutEventsProducer eventsProducer;

    @Autowired
    AvroSchemaRegistryClientMessageConverter avroConverter;

    @Autowired
    OrderRepository orderRepository;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    @Value("${spring.cloud.stream.schema-registry-client.endpoint}")
    String schemaRegistryUrl;

    WebTestClient webTestClient;
    KafkaConsumer<String, byte[]> consumer;
    final List<ConsumerRecord<String, byte[]>> received = new ArrayList<>();
    /** Filters Kafka records. Also set for fixture ORD-006, which cleanup must not delete. */
    String orderId;
    /** Row this test inserted via start-checkout. Unset for fixture ORD-006. */
    String createdOrderId;

    @BeforeEach
    void setup() {
        orderId = null;
        createdOrderId = null;
        received.clear();
        webTestClient = MockMvcWebTestClient.bindToApplicationContext(context).build();
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "place-order-flow-kafka-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(
                ORDER_CREATED_TOPIC,
                ORDER_CONFIRMED_TOPIC,
                ORDER_CANCELLED_TOPIC,
                STOCK_UNAVAILABLE_TOPIC,
                ORDER_CONFIRMATION_REJECTED_TOPIC));
    }

    @AfterEach
    void cleanup() {
        consumer.close();
        if (createdOrderId != null) {
            orderRepository.findByOrderId(createdOrderId).ifPresent(orderRepository::delete);
        }
    }

    @Test
    void startCheckoutThenPaymentAuthorizedOnKafkaConfirmsTheOrder() {
        assertFalse(
                eventsProducer.getClass().getSimpleName().startsWith("InMemory"),
                "the real producer must be wired, not " + eventsProducer.getClass());

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
        long startedAt = System.currentTimeMillis();
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
                .returnResult()
                .getResponseBody();
        var json = new String(body, StandardCharsets.UTF_8);
        orderId = JsonPath.read(json, "$.orderId");
        createdOrderId = orderId;
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();

        // OrderCreated on orders-checkout.order-created.event.avro.v1
        var created = awaitOne(ORDER_CREATED_TOPIC, OrderCreated.class, startedAt);
        assertEquals(orderId, created.getOrderId().toString());
        assertEquals(Double.valueOf(21.0), created.getAmount());

        // PaymentAuthorized on payments-processing.payment-authorized.event.avro.v1, as payments would publish it
        var payment = new PaymentAuthorized();
        payment.setOrderId(orderId);
        payment.setCustomerId("CUS-100");
        payment.setAmount(21.0);
        payment.setCurrency("EUR");
        publish(PAYMENT_AUTHORIZED_TOPIC, orderId, payment);

        // OrderConfirmed on orders-checkout.order-confirmed.event.avro.v1
        var confirmed = awaitOne(ORDER_CONFIRMED_TOPIC, OrderConfirmed.class, startedAt);
        assertEquals(orderId, confirmed.getOrderId().toString());
        Awaitility.await()
                .atMost(TIMEOUT)
                .until(() ->
                        orderRepository.findByOrderId(orderId).orElseThrow().getStatus()
                                == com.arcadiaeditions.orders.checkout.core.domain.OrderStatus.CONFIRMED);
        // the event carries the version flushed by confirmOrder; the commit may add one spurious UPDATE (JSON value
        // types without equals), so the row can be ahead of it, never behind
        int committed = orderRepository.findByOrderId(orderId).orElseThrow().getVersion();
        assertTrue(confirmed.getVersion() > created.getVersion(), "confirmed after created");
        assertTrue(confirmed.getVersion() <= committed, "event " + confirmed.getVersion() + " vs row " + committed);
        assertEquals(
                1,
                recordsFor(ORDER_CREATED_TOPIC, OrderCreated.class, startedAt).size());

        // both schemas were registered in the compose Schema Registry
        String subjects = RestClient.create(schemaRegistryUrl)
                .get()
                .uri("/subjects")
                .retrieve()
                .body(String.class);
        assertTrue(subjects.contains("ordercreated"), subjects);
        assertTrue(subjects.contains("orderconfirmed"), subjects);

        webTestClient
                .method(GET)
                .uri("/api/orders/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(200)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("CONFIRMED");
    }

    /**
     * S3: an order created here (not fixture ORD-003; this test is not transactional) is cancelled when
     * StockReleased PAYMENT_DECLINED arrives, and OrderCancelled is on its topic. C-03's reason.
     */
    @Test
    void stockReleasedOnKafkaCancelsTheOrder() {
        assertFalse(
                eventsProducer.getClass().getSimpleName().startsWith("InMemory"),
                "the real producer must be wired, not " + eventsProducer.getClass());

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
        long startedAt = System.currentTimeMillis();
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
                .returnResult()
                .getResponseBody();
        var json = new String(body, StandardCharsets.UTF_8);
        orderId = JsonPath.read(json, "$.orderId");
        createdOrderId = orderId;
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();

        var created = awaitOne(ORDER_CREATED_TOPIC, OrderCreated.class, startedAt);
        assertEquals(orderId, created.getOrderId().toString());

        // StockReleased on catalog-inventory.stock-released.event.avro.v1, as inventory would publish it
        var released = new StockReleased();
        released.setOrderId(orderId);
        released.setReason(StockReleaseReason.PAYMENT_DECLINED);
        released.setItems(List.of());
        publish(STOCK_RELEASED_TOPIC, orderId, released);

        // OrderCancelled on orders-checkout.order-cancelled.event.avro.v1
        var cancelled = awaitOne(ORDER_CANCELLED_TOPIC, OrderCancelled.class, startedAt);
        assertEquals(orderId, cancelled.getOrderId().toString());
        assertEquals("PAYMENT_DECLINED", cancelled.getReason().toString());
        assertEquals("CUS-100", cancelled.getCustomerId().toString());
        assertEquals("reader@example.com", cancelled.getCustomerEmail().toString());
        assertEquals(TestClock.NOW.toString(), cancelled.getCancelledAt().toString());
        Awaitility.await()
                .atMost(TIMEOUT)
                .until(() ->
                        orderRepository.findByOrderId(orderId).orElseThrow().getStatus() == OrderStatus.CANCELLED);
        int committed = orderRepository.findByOrderId(orderId).orElseThrow().getVersion();
        assertTrue(cancelled.getVersion() > created.getVersion(), "cancelled after created");
        assertTrue(cancelled.getVersion() <= committed, "event " + cancelled.getVersion() + " vs row " + committed);
        assertEquals(
                1,
                recordsFor(ORDER_CANCELLED_TOPIC, OrderCancelled.class, startedAt)
                        .size());
        assertSubjectRegistered("stockreleased");
        assertSubjectRegistered("ordercancelled");

        webTestClient
                .method(GET)
                .uri("/api/orders/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(200)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("CANCELLED")
                .jsonPath("$.cancellationReason")
                .isEqualTo("PAYMENT_DECLINED")
                .jsonPath("$.cancelledAt")
                .isEqualTo(TestClock.NOW.toString());
    }

    /**
     * S2 stockGone: SKU-004 is unavailable in the inventory stub. 409, no row, one StockUnavailable, no OrderCreated.
     */
    @Test
    void startCheckoutWithSku004PublishesStockUnavailable() {
        assertFalse(
                eventsProducer.getClass().getSimpleName().startsWith("InMemory"),
                "the real producer must be wired, not " + eventsProducer.getClass());

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
        long startedAt = System.currentTimeMillis();
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
                .returnResult()
                .getResponseBody();
        var json = new String(body, StandardCharsets.UTF_8);
        orderId = JsonPath.read(json, "$.orderId");
        createdOrderId = orderId;

        assertTrue(orderRepository.findByOrderId(orderId).isEmpty());
        assertEquals(before, orderRepository.count());

        // StockUnavailable on orders-checkout.stock-unavailable.event.avro.v1
        var unavailable = awaitOne(STOCK_UNAVAILABLE_TOPIC, StockUnavailable.class, startedAt);
        assertEquals(orderId, unavailable.getOrderId().toString());
        assertEquals("CUS-100", unavailable.getCustomerId().toString());
        assertEquals("reader@example.com", unavailable.getCustomerEmail().toString());
        assertEquals(1, unavailable.getUnavailableItems().size());
        assertEquals(
                "SKU-004", unavailable.getUnavailableItems().get(0).getSku().toString());
        assertEquals(3, unavailable.getUnavailableItems().get(0).getRequestedQuantity());
        assertEquals(0, unavailable.getUnavailableItems().get(0).getAvailableQuantity());
        assertEquals(
                1,
                recordsFor(STOCK_UNAVAILABLE_TOPIC, StockUnavailable.class, startedAt)
                        .size());
        assertEquals(
                0,
                recordsFor(ORDER_CREATED_TOPIC, OrderCreated.class, startedAt).size());
        assertSubjectRegistered("stockunavailable");
    }

    /**
     * S4 order-cancelled outcome: C-06 ORD-006 is already CANCELLED. PaymentAuthorized emits one
     * OrderConfirmationRejected (ORDER_CANCELLED) and leaves the fixture row unchanged.
     */
    @Test
    void paymentAuthorizedForOrd006PublishesOrderConfirmationRejected() {
        assertFalse(
                eventsProducer.getClass().getSimpleName().startsWith("InMemory"),
                "the real producer must be wired, not " + eventsProducer.getClass());

        orderId = "ORD-006";
        long id = orderRepository.findByOrderId(orderId).orElseThrow().getId();
        long startedAt = System.currentTimeMillis();

        var payment = new PaymentAuthorized();
        payment.setOrderId(orderId);
        payment.setCustomerId("CUST-006");
        payment.setAmount(29.99);
        payment.setCurrency("EUR");
        publish(PAYMENT_AUTHORIZED_TOPIC, orderId, payment);

        // OrderConfirmationRejected on orders-checkout.order-confirmation-rejected.event.avro.v1
        var rejected = awaitOne(ORDER_CONFIRMATION_REJECTED_TOPIC, OrderConfirmationRejected.class, startedAt);
        assertEquals(orderId, rejected.getOrderId().toString());
        assertEquals("CUST-006", rejected.getCustomerId().toString());
        assertEquals("ORDER_CANCELLED", rejected.getReason().toString());
        assertEquals(TestClock.NOW.toString(), rejected.getRejectedAt().toString());
        assertEquals(
                1,
                recordsFor(ORDER_CONFIRMATION_REJECTED_TOPIC, OrderConfirmationRejected.class, startedAt)
                        .size());
        assertEquals(
                0,
                recordsFor(ORDER_CONFIRMED_TOPIC, OrderConfirmed.class, startedAt)
                        .size());
        assertSubjectRegistered("orderconfirmationrejected");

        var persisted = orderRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, persisted.getStatus());
        assertEquals("RESERVATION_EXPIRED", persisted.getCancellationReason());

        webTestClient
                .method(GET)
                .uri("/api/orders/{id}", id)
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

    private void publish(String topic, String key, Object payload) {
        var avro = avroConverter.toMessage(
                payload, new MessageHeaders(Map.of(MessageHeaders.CONTENT_TYPE, "application/*+avro")));
        String contentType = String.valueOf(avro.getHeaders().get(MessageHeaders.CONTENT_TYPE));
        var props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        try (var producer = new KafkaProducer<String, byte[]>(props)) {
            var record = new ProducerRecord<>(topic, key, (byte[]) avro.getPayload());
            record.headers().add(MessageHeaders.CONTENT_TYPE, contentType.getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        } catch (Exception e) {
            throw new IllegalStateException("could not publish to " + topic, e);
        }
    }

    private <T> T awaitOne(String topic, Class<T> type, long since) {
        Awaitility.await().atMost(TIMEOUT).until(() -> {
            consumer.poll(Duration.ofMillis(500)).forEach(received::add);
            return !recordsFor(topic, type, since).isEmpty();
        });
        return recordsFor(topic, type, since).getFirst();
    }

    private <T> List<T> recordsFor(String topic, Class<T> type, long since) {
        return received.stream()
                .filter(r -> r.topic().equals(topic) && r.timestamp() >= since)
                .map(r -> decode(r, type))
                .filter(e -> orderId.equals(orderIdOf(e)))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private <T> T decode(ConsumerRecord<String, byte[]> record, Class<T> type) {
        Header header = record.headers().lastHeader(MessageHeaders.CONTENT_TYPE);
        String contentType = header == null
                ? "application/*+avro"
                : new String(header.value(), StandardCharsets.UTF_8).replace("\"", "");
        var message = MessageBuilder.withPayload(record.value())
                .setHeader(MessageHeaders.CONTENT_TYPE, contentType)
                .build();
        return (T) avroConverter.fromMessage(message, type);
    }

    private static String orderIdOf(Object event) {
        if (event instanceof OrderCreated e) {
            return e.getOrderId().toString();
        }
        if (event instanceof OrderConfirmed e) {
            return e.getOrderId().toString();
        }
        if (event instanceof OrderCancelled e) {
            return e.getOrderId().toString();
        }
        if (event instanceof StockUnavailable e) {
            return e.getOrderId().toString();
        }
        if (event instanceof OrderConfirmationRejected e) {
            return e.getOrderId().toString();
        }
        return null;
    }

    private void assertSubjectRegistered(String subjectFragment) {
        String subjects = RestClient.create(schemaRegistryUrl)
                .get()
                .uri("/subjects")
                .retrieve()
                .body(String.class);
        assertTrue(subjects.contains(subjectFragment), subjects);
    }
}
