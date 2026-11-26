# PlaceOrderFlow: tasks

flow: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl
baseline: baseline/place-order-flow-5
branch: afk/place-order-flow

<!--
Scope: the OrdersCheckout bounded context (this repository) only. The steps of the other contexts (inventory,
payments, fulfillment, notifications) are boards of their own repositories; here they are stubs or Kafka messages.
Model paths: ZFL = ../arcadia-editions-architecture/business-flows/place-order-flow.zfl,
ZDL = ../orders-checkout-api/domain-model.zdl, inventory ZDL = ../catalog-inventory-api/domain-model.zdl.
Test data chains C-01..C-10: header of src/test/resources/data.sql.
Approved models: orders-checkout-api service-all 0.2.0 (tag release/service-all/v0.2.0), catalog-inventory-api
service-all 0.2.0 (consumed Avro); ZFL at arcadia-editions-architecture tag baseline/place-order-flow-<n>.
-->

## S0 Baseline
review: [x] claude-opus (fix round), .zenwave/handoff/place-order-flow/reports/review-S0b.md

- [x] T-01 mechanical AFK Generated tests that should pass do: getOrderTest with a fixture id, updateTest without Instant.now()/OrderItem equality, API tests with a datasource
      test data: data/jpa/Order/1.json, data.sql (C-01, id 1)
      tests: OrdersCheckoutServiceTest, OrderRepositoryIntegrationTest, OrdersCheckoutApiControllerTest#getOrderTest, OrdersCheckoutApiIntegrationTest#testGetOrder_200, OrdersCheckoutApiIntegrationTest#testStartOrderCheckout_400
      done: grok grok-4.7
- [x] T-02 mechanical AFK Inject a java.time.Clock (system clock in main, fixed 2026-10-14T10:00:00Z in tests) for every timestamp the service sets
      tests: OrdersCheckoutServiceTest
      done: grok grok-4.7
- [x] T-03 mechanical AFK OrderRepository.findByOrderId (JPA and in-memory twin): commands correlate by orderId
      model: ../orders-checkout-api/domain-model.zdl:58, ../orders-checkout-api/domain-model.zdl:113-121
      test data: data.sql (C-02, C-06)
      tests: OrderRepositoryIntegrationTest
      done: grok grok-4.7
- [x] T-04 bounded HITL Decide how orderId is generated (format, who generates it) and record it as an ADR; ZDL and ZFL do not say
      model: ../orders-checkout-api/domain-model.zdl:58, ../orders-checkout-api/domain-model.zdl:92-111
      done: person (HITL), docs/adr/0001-order-id.md
- [x] T-05 advanced AFK Outbound port InventoryClient (reserveStock, confirmStockReservation) with its REST adapter on catalog-inventory /inventory/reservations/{reserve,confirm}, and an in-memory stub for tests (SKU-004 unavailable, NOT_HELD by orderId)
      model: ../orders-checkout-api/domain-model.zdl:129, ../orders-checkout-api/domain-model.zdl:138, ../catalog-inventory-api/domain-model.zdl:115-145, ../catalog-inventory-api/domain-model.zdl:169-171, ../catalog-inventory-api/domain-model.zdl:192-194, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:101-105, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:171-176
      tests: InventoryClientStubTest
      done: claude-code claude-opus
      blocked by: T-01

- [x] T-18 mechanical AFK ArchitectureTest: let the core reach the generated AsyncAPI SDK and Avro models (asyncapi.provider, asyncapi.client, events.avro), outside the hexagon by design (overlay Shape); respectsLayersForHexagonalArchitecture green, no other rule weakened
      tests: ArchitectureTest
      decided by: person, 2026-11-25 (option 1)
      done: grok grok-4.7
- [x] T-19 mechanical AFK S0 review fixes: drop OrdersCheckoutServiceImpl.clock() accessor and the T-02 tests that compare a constant with itself (keep the profile test, move it to a config test); TestClock.NOW instead of the literal in OrderRepositoryIntegrationTest and OrdersCheckoutServiceTest, stale "T-02" comment; one assertEquals style; InventoryClientRestAdapter throws when the body for the outcome is missing; unit test for UuidV7OrderIdGenerator (version 7, variant, time order)
      fixes: .zenwave/handoff/place-order-flow/reports/review-S0.md
      tests: OrdersCheckoutServiceTest, InventoryClientRestAdapterTest, OrderRepositoryIntegrationTest, UuidV7OrderIdGeneratorTest
      blocked by: T-06
      done: claude-code claude-sonnet

## S1 completed (tracer bullet)
path: StartOrderCheckout > startOrderCheckout > reserveStock[StockReservedResult] > OrderCreated
      > authorizePayment[authorized] > PaymentAuthorized > confirmOrder[confirmed] > confirmStockReservation
      > OrderConfirmed > ... > end completed
review: [ ]

- [x] T-06 bounded AFK startOrderCheckout reserved: new orderId, reserveStock, totalAmount = sum(quantity * unitPrice), CREATED + createdAt, persist, emit only OrderCreated
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:79-93, ../orders-checkout-api/domain-model.zdl:126-130, ../orders-checkout-api/domain-model.zdl:150-160
      test data: data.sql (C-02 for the shape of a CREATED order)
      tests: OrdersCheckoutServiceTest#startOrderCheckoutTest, OrdersCheckoutApiControllerTest#startOrderCheckoutTest
      blocked by: T-02, T-03, T-04, T-05
      done: claude-code claude-sonnet
- [x] T-07 bounded AFK confirmOrder confirmed: load by orderId, confirmStockReservation, CONFIRMED + confirmedAt, persist, emit OrderConfirmed with items and shippingAddress; no-op on CONFIRMED (redelivery)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:155-164, ../orders-checkout-api/domain-model.zdl:132-139, ../orders-checkout-api/domain-model.zdl:174-193
      test data: data.sql (C-02 confirmed, C-01 redelivery)
      tests: OrdersCheckoutServiceTest#confirmOrderTest
      blocked by: T-02, T-03, T-05
      done: claude-code claude-sonnet
- [x] T-08 mechanical AFK PaymentAuthorizedConsumerService: map PaymentAuthorized.orderId to ConfirmOrderInput and call confirmOrder
      model: ../orders-checkout-api/domain-model.zdl:136, ../orders-checkout-api/domain-model.zdl:113-115
      test data: data.sql (C-02)
      tests: PaymentAuthorizedConsumerServiceTest
      blocked by: T-07
      done: grok grok-4.7
- [x] T-09 bounded AFK Flow test completed (orders side): POST start-checkout 201 emits OrderCreated; PaymentAuthorized confirms it and emits OrderConfirmed; GET shows CONFIRMED
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:340-342
      test data: data.sql (C-01, C-02)
      tests: OrdersCheckoutApiIntegrationTest#testStartOrderCheckout_201, PlaceOrderFlowCompletedIntegrationTest
      blocked by: T-06, T-08
      done: claude-code claude-sonnet

- [/] T-20 advanced AFK Kafka wiring of the slice: application.yml spring.cloud.stream bindings for the consumers payment-authorized-event-v1 and stock-released-event-v1 (in-0, group orders-checkout) and destinations of every *-out binding as asyncapi.yml / asyncapi-client.yml name the topics, Avro content type and Schema Registry; one Kafka Testcontainers test (docker-compose) that publishes PaymentAuthorized to its topic and consumes OrderCreated and OrderConfirmed from theirs, without the in-memory producer
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:113, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:155, ../orders-checkout-api/domain-model.zdl:136, ../orders-checkout-api/domain-model.zdl:142, ../orders-checkout-api/asyncapi.yml, ../orders-checkout-api/asyncapi-client.yml
      fixes: .zenwave/handoff/place-order-flow/reports/review-S1.md (FIX FIRST)
      tests: PlaceOrderFlowKafkaIntegrationTest
- [x] T-21 bounded AFK S1 review fixes: recording InventoryClientStub and asserts that startOrderCheckout reserved the persisted orderId with the requested sku/quantity lines; OrderConfirmed.version after the confirm write (assert in PlaceOrderFlowCompletedIntegrationTest, emit after flush if stale); TestClock.NOW in OrdersCheckoutApiControllerTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S1.md (SHOULD FIX, NOTEs)
      tests: OrdersCheckoutServiceTest, OrdersCheckoutApiControllerTest, PlaceOrderFlowCompletedIntegrationTest
      blocked by: T-10
      done: claude-code claude-sonnet

## S2 stockGone
path: StartOrderCheckout > startOrderCheckout > reserveStock[StockUnavailableResult] > StockUnavailable
      > sendStockUnavailableNotification > end stockGone
review: [ ]

- [x] T-10 bounded AFK startOrderCheckout unavailable: persist nothing, emit StockUnavailable (orderId, customer, unavailableItems from the result), answer 409 with StockUnavailable body
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:88-92, ../orders-checkout-api/domain-model.zdl:126-130, ../orders-checkout-api/domain-model.zdl:162-172, ../orders-checkout-api/openapi-overlay.yml
      test data: inventory stub SKU-004 (stub-driven, no row: the order is never persisted)
      tests: OrdersCheckoutServiceTest#startOrderCheckoutStockUnavailableTest, OrdersCheckoutApiIntegrationTest#testStartOrderCheckout_409
      blocked by: T-06
      done: claude-code claude-sonnet
- [x] T-11 bounded AFK Flow test stockGone (orders side): POST start-checkout with SKU-004 answers 409, no Order row, StockUnavailable emitted
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:340-343
      tests: PlaceOrderFlowStockGoneIntegrationTest
      blocked by: T-10
      done: claude-code claude-sonnet

- [x] T-22 mechanical AFK S2 review fixes: remove the dead generated EventsMapper.asStockUnavailable(Order); PlaceOrderFlowStockGoneIntegrationTest checks "no OrderCreated" with the typed getOrderId() filter like createdFor
      fixes: .zenwave/handoff/place-order-flow/reports/review-S2.md (NOTEs)
      tests: OrdersCheckoutServiceTest, PlaceOrderFlowStockGoneIntegrationTest
      done: grok grok-4.7

## S3 orderCancelled
path: ... > PaymentDeclined | PaymentRetryExhausted | PaymentVoided | PaymentVoidFailed | ReservationExpired
      > releaseStock / expireReservation > StockReleased > cancelOrder > OrderCancelled
      > sendOrderCancelledNotification > end orderCancelled
review: [ ]

- [x] T-12 bounded AFK cancelOrder: load by orderId, CREATED or CONFIRMED to CANCELLED, cancellationReason = input.reason, cancelledAt, persist, emit OrderCancelled; no-op, no event, on CANCELLED
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:300-310, ../orders-checkout-api/domain-model.zdl:117-121, ../orders-checkout-api/domain-model.zdl:141-144, ../orders-checkout-api/domain-model.zdl:195-205
      test data: data.sql (C-03 from CREATED, C-04 from CONFIRMED, C-07 redelivery)
      tests: OrdersCheckoutServiceTest#cancelOrderTest
      blocked by: T-02, T-03
      done: claude-code claude-sonnet
- [x] T-13 mechanical AFK StockReleasedConsumerService: map StockReleased (orderId, reason name) to CancelOrderInput and call cancelOrder
      model: ../orders-checkout-api/domain-model.zdl:142, ../orders-checkout-api/domain-model.zdl:117-121, ../catalog-inventory-api/domain-model.zdl:74-80
      test data: data.sql (C-03)
      tests: StockReleasedConsumerServiceTest
      blocked by: T-12
      done: grok grok-4.7
- [ ] T-14 bounded AFK Flow test orderCancelled (orders side), one case per cause: PAYMENT_DECLINED, PAYMENT_RETRY_EXHAUSTED, RESERVATION_EXPIRED from CREATED; PAYMENT_VOIDED, PAYMENT_VOID_FAILED from CONFIRMED; GET shows CANCELLED with the reason
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:262-266, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:340-344
      test data: data.sql (C-03, C-04, C-06..C-10)
      tests: PlaceOrderFlowCancelledIntegrationTest
      blocked by: T-13

## S4 late authorization rejected
path: ... > PaymentAuthorized > confirmOrder[stock-not-held | order-cancelled] > OrderConfirmationRejected
      > voidPayment > PaymentVoided > releaseStock > StockReleased > cancelOrder > end orderCancelled
review: [ ]

- [x] T-15 bounded AFK confirmOrder rejected: on CANCELLED emit OrderConfirmationRejected ORDER_CANCELLED; on NOT_HELD emit it with STOCK_NOT_HELD; no state change (EventsMapper asOrderConfirmationRejected with reason and rejectedAt)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:147-164, ../orders-checkout-api/domain-model.zdl:132-139, ../orders-checkout-api/domain-model.zdl:206-214, ../catalog-inventory-api/domain-model.zdl:115-125
      test data: data.sql (C-05 stock-not-held, C-06 order-cancelled)
      tests: OrdersCheckoutServiceTest#confirmOrderRejectedTest
      blocked by: T-07
      done: claude-code claude-sonnet
- [ ] T-16 bounded AFK Flow test late authorization (orders side): PaymentAuthorized for C-05 and C-06 emits OrderConfirmationRejected and leaves the order unchanged
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:155-164
      test data: data.sql (C-05, C-06)
      tests: PlaceOrderFlowLateAuthorizationIntegrationTest
      blocked by: T-08, T-15

## S5 polish
review: [ ]

- [x] T-17 mechanical AFK getOrder read side: 200 for CREATED (C-02), CONFIRMED (C-01), CANCELLED (C-06) and 404 for an unknown id
      model: ../orders-checkout-api/domain-model.zdl:146-147
      test data: data.sql (C-01, C-02, C-06)
      tests: OrdersCheckoutApiIntegrationTest#testGetOrder_200, OrdersCheckoutApiIntegrationTest#testGetOrder_400, OrdersCheckoutApiControllerTest#getOrderTest
      blocked by: T-01
      done: grok grok-4.7
