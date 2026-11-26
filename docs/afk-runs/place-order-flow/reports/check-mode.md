# Check mode: place-order-flow, OrdersCheckout context

skill: zenwave-tasks-from-code-and-models (check mode)
checkout: .zenwave/work/integration, branch afk/place-order-flow @ 43f010d (clean tree)
models: ZFL arcadia-editions-architecture/business-flows/place-order-flow.zfl, ZDL orders-checkout-api/domain-model.zdl (0.2.0), inventory ZDL catalog-inventory-api/domain-model.zdl
suite evidence: logs/suite-48c545b.log (green, 36 unit / 22 IT); after it only T-26 (test-only)

Paths abbreviated: `Impl` = src/main/.../core/application/OrdersCheckoutServiceImpl.java, `EM` = .../core/application/mappers/EventsMapper.java,
`SvcT` = src/test/.../core/application/OrdersCheckoutServiceTest.java, `ApiIT` = .../adapters/web/OrdersCheckoutApiIntegrationTest.java,
`CtlT` = .../adapters/web/OrdersCheckoutApiControllerTest.java, `Flow*IT` = .../adapters/web/PlaceOrderFlow*IntegrationTest.java,
`KafkaIT` = .../adapters/events/PlaceOrderFlowKafkaIntegrationTest.java.

## Step 2: models hold

- Lint not run (instructed: git + read only). Models pinned at baseline/place-order-flow-5, passed gate.
- Identities: ZFL systems.OrdersCheckout commands (zfl:10) = ZDL startOrderCheckout (zdl:130), confirmOrder (zdl:139), cancelOrder (zdl:144). Events OrderCreated (zdl:151), StockUnavailable (zdl:163), OrderConfirmed (zdl:175), OrderConfirmationRejected (zdl:208), OrderCancelled (zdl:196) exist. Consumed StockReleased (inventory zdl:197), PaymentAuthorized (payments asyncapi, zdl:136). `@compensates(startOrderCheckout)` (zfl:305) names a command. OK.
- Proposals: P-01 accepted A, P-02 accepted A. None open.
- `grep TODO src/main`: none.

## Step 3: paths (orders side)

- S1 completed: StartOrderCheckout > startOrderCheckout > reserveStock[StockReservedResult] > OrderCreated > (payments) PaymentAuthorized > confirmOrder[confirmed] > confirmStockReservation > OrderConfirmed > (other contexts) > end completed
- S2 stockGone: StartOrderCheckout > startOrderCheckout > reserveStock[StockUnavailableResult] > StockUnavailable > (notifications) > end stockGone
- S3 orderCancelled: PaymentDeclined | PaymentRetryExhausted | PaymentVoided | PaymentVoidFailed | ReservationExpired > (inventory) StockReleased > cancelOrder > OrderCancelled > (fulfillment, notifications) > end orderCancelled
- S4 late authorization: PaymentAuthorized > confirmOrder[stock-not-held | order-cancelled] > OrderConfirmationRejected > (payments voidPayment ... StockReleased) > cancelOrder > end orderCancelled
- end notificationFailed: notifications context only, nothing here.

## Step 4: coverage, element by element

| Model element | Code | Test |
|---|---|---|
| zfl:79-86 start StartOrderCheckout (Customer, POST) / zdl:127 `@post("/start-checkout")` | OrdersCheckoutApiController.java:92-98 (201) | ApiIT:18 testStartOrderCheckout_201; CtlT:32; ApiIT:100 _400 validation |
| zfl:88-90, zdl:129 `@calls reserveStock` | Impl:60-65 (orderId from generator, ADR 0001), InventoryClientRestAdapter | SvcT:78-84 (recorded call, orderId + lines); InventoryClientRestAdapterTest:45,70; InventoryClientStubTest:23,37 |
| zdl:128 `@transition(to: CREATED)`, createdAt, totalAmount, persist | Impl:78-85 | SvcT:43-66 (CREATED, 99.97, createdAt=TestClock.NOW, persisted); ApiIT:18 (totalAmount 21.0, createdAt) |
| zfl:91 on StockReservedResult emits OrderCreated / zdl:151-160 | Impl:87-88, EM:14-15 (amount<-totalAmount) | SvcT:68-76 (orderId, amount, currency, version; only OrderCreated); FlowCompletedIT:76; KafkaIT:153-155 (on topic). customerId, customerEmail, paymentMethodId of the event: not asserted anywhere -> GAP 2 |
| zfl:92 on StockUnavailableResult emits StockUnavailable / zdl:126 persist nothing, 409 / zdl:163-172 | Impl:66-76, EM:28-38, WebExceptionsTranslator.java:148-161 (409 body), LoggingAspect DEBUG (T-24) | SvcT:88-125; ApiIT:139 _409; FlowStockGoneIT:32-61 (409 body, no row, 1 StockUnavailable, no OrderCreated) |
| zfl:155 when PaymentAuthorized do confirmOrder / zdl:136 `@asyncapi payment-authorized-event-v1` | PaymentAuthorizedConsumerService.java:21-25; application.yml:239,247-250 | PaymentAuthorizedConsumerServiceTest:33; KafkaIT:117 (over Kafka, binding started) |
| zdl:137 `@transition(from: CREATED, to: CONFIRMED)`, load by orderId, guard | Impl:96, 104 (OrderTransitions.java:9-11), 109-111; OrderRepository.findByOrderId (T-03) | SvcT:128-149; OrderRepositoryIntegrationTest:37 |
| zfl:157, zdl:138 `@calls confirmStockReservation` | Impl:105 | SvcT:145 (called with ORD-002); InventoryClientRestAdapterTest:90,111 |
| zfl:160-161 @outcome(confirmed) emits OrderConfirmed / zdl:175-193 | Impl:112-114, EM:17-18 | SvcT:136-148 (customerId, confirmedAt, items, shippingAddress); FlowCompletedIT:83-110 (version == persisted, GET CONFIRMED); KafkaIT:166-177 |
| zfl:158-159 @outcome(stock-not-held) on StockReservationNotHeldResult emits OrderConfirmationRejected / zdl:208-214 | Impl:106-107, 118-123, EM:20-23 | SvcT:155-169 (STOCK_NOT_HELD, rejectedAt, no change); FlowLateAuthorizationIT:43-73 (C-05) |
| zfl:162-163 @outcome(order-cancelled) emits OrderConfirmationRejected | Impl:101-103 (no inventory call) | SvcT:171-185; FlowLateAuthorizationIT:74-105 (C-06) |
| zfl:67-68, zdl:133 redelivery no-op on CONFIRMED | Impl:97-100 | SvcT:189-201 (C-01, no inventory call, no event) |
| zfl:306 when StockReleased do cancelOrder / zdl:142 `@asyncapi stock-released-event-v1` | StockReleasedConsumerService.java:47-52 (reason = StockReleaseReason name); application.yml:239,251-254 | StockReleasedConsumerServiceTest:34 (direct call). Binding never started in any test (application-test.yml:17 auto-startup false, only payment-authorized is started in KafkaIT:61) -> GAP 1 |
| zdl:143 `@transition(from: [CREATED, CONFIRMED], to: CANCELLED)`, reason, cancelledAt, persist | Impl:129, 134-138; OrderTransitions.java:13-15 | SvcT:213-233 (C-03, C-04); FlowCancelledIT:167-221 (5 causes, GET CANCELLED + reason + cancelledAt) |
| zfl:308 emits OrderCancelled / zdl:196-204 | Impl:139-141, EM:25-26 | SvcT:250-258; FlowCancelledIT:193-204 (reason, version, customerId, customerEmail) |
| zfl:303, zdl:141 redelivery no-op on CANCELLED | Impl:130-133 | SvcT:236-248 (C-07) |
| zfl:275-291 ReservationExpired (owned by inventory) -> StockReleased RESERVATION_EXPIRED | via cancelOrder | FlowCancelledIT:171 (RESERVATION_EXPIRED); FlowLateAuthorizationIT (C-05/C-06 after expiry) |
| zdl:146-147 `getOrder(id)` read side (actor sees async outcome) | OrdersCheckoutApiController.java:100-110 | ApiIT:192 _200, :263 _400, :278 _404; CtlT:62-70 (CREATED, CONFIRMED, CANCELLED, 404) |
| Published topics zdl:150,162,174,195,207 / asyncapi.yml | application.yml:255-270 (5 *-out bindings) | Only order-created and order-confirmed proven on Kafka (KafkaIT:153,166). stock-unavailable, order-confirmation-rejected, order-cancelled bindings untested on Kafka -> GAP 1 |
| Test data: every precondition/branch/outcome | data.sql header C-01..C-10, data/jpa/Order/1..10.json; StockUnavailable and NOT_HELD stub-driven (InventoryClientStub.java:29,31) | all chains used (C-08..C-10 only as read fixtures; their causes driven from C-03/C-04 in FlowCancelledIT) |

Known with the person, not listed: T-23 Avro wire format, unknown-orderId policy, DLQ/x-error-topics, events before commit, unkeyed records, extra UPDATE (JSON value types), 404 not in openapi, amount as double, confirmedAt source, hand-written inventory client. GAP 1 depends on T-23 for cross-service interop, but not to prove this service's own bindings.

## GAPS

- [ ] T-NN mechanical AFK Kafka wiring of the cancelled, stockGone and late-authorization paths: StockReleased published on catalog-inventory.stock-released.event.avro.v1 reaches cancelOrder through stock-released-event-v1-in-0 (started in the test) and OrderCancelled lands on orders-checkout.order-cancelled.event.avro.v1; start-checkout with SKU-004 puts StockUnavailable on orders-checkout.stock-unavailable.event.avro.v1; PaymentAuthorized for ORD-006 puts OrderConfirmationRejected on orders-checkout.order-confirmation-rejected.event.avro.v1; same pattern as PlaceOrderFlowKafkaIntegrationTest
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:88-92, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:155-164, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:305-309, ../orders-checkout-api/domain-model.zdl:142, ../orders-checkout-api/domain-model.zdl:162, ../orders-checkout-api/domain-model.zdl:195, ../orders-checkout-api/domain-model.zdl:207, ../orders-checkout-api/asyncapi.yml, ../orders-checkout-api/asyncapi-client.yml
      test data: data.sql (C-03 CREATED for StockReleased, C-06 for the rejection; SKU-004 stub-driven)
      tests: PlaceOrderFlowKafkaIntegrationTest
      should have covered: T-20 (scoped to the S1 slice only)
- [ ] T-NN mechanical AFK OrderCreated carries what authorizePayment needs: assert customerId, customerEmail and paymentMethodId of the emitted OrderCreated (unit and flow test), not only orderId/amount/currency/version
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:91, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:114-122, ../orders-checkout-api/domain-model.zdl:150-160
      test data: none (startOrderCheckout input in the test; inventory stub reserves)
      tests: OrdersCheckoutServiceTest#startOrderCheckoutTest, PlaceOrderFlowCompletedIntegrationTest
      should have covered: T-06
