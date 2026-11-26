VERDICT: PASS
SLICE: S1   RANGE: 4b727e7..a70304b (S1 commits a3b0399 T-06, 1c1a50f T-07, 68bccb5 T-08, d258f22 T-09, 0c5896a T-21, fea7f64 T-20; checkout .zenwave/work/suite, tree clean, nothing changed)
SUITE: not rerun (orchestrator's agreement: Docker ports shared). Used the full run at a70304b: log .zenwave/handoff/place-order-flow/logs/suite-a70304b.log (quiet mode, no per-test lines), reports target/surefire-reports and target/failsafe-reports (written 2026-11-26 02:42-02:45, after the a70304b commit at 02:39).
  Counted from the XML: surefire 32 run, 0 failures, 0 errors, 0 skipped. Failsafe 22 run, 0 red: PlaceOrderFlowKafkaIntegrationTest 1, OrdersCheckoutApiIntegrationTest 6, PlaceOrderFlowCompletedIntegrationTest 1, PlaceOrderFlowCancelled 5, LateAuthorization 2, StockGone 1, OrderRepositoryIntegrationTest 6. Single run, so nothing flaky seen.
BASELINE: untouched (git diff --stat 4b727e7..a70304b over *.zfl, *.zdl, *openapi*.yml, *asyncapi*.yml and src/test/resources/data* is empty).

PREVIOUS FINDINGS (review-S1.md)
- FIX FIRST T-08/T-09, Kafka wiring: resolved by T-20.
  - application.yml:46-80: spring.cloud.function.definition binds payment-authorized-event-v1 and stock-released-event-v1, in-0 group orders_checkout (x-groupId).
  - The 7 destinations match the channel addresses of asyncapi.yml:24-60 and the producers' asyncapi.yml. Avro content type is set, and the registry comes from SchemaRegistryConfiguration.
  - PlaceOrderFlowKafkaIntegrationTest (green) runs the real producer over Kafka: OrderCreated on its topic, then PaymentAuthorized published to the payments topic, then OrderConfirmed on its topic, then GET CONFIRMED.
  - The wire format (SCSt converter, not the Confluent serde) is T-23, with the person. Not a finding.
- SHOULD FIX T-06, reserveStock unproven: resolved. OrdersCheckoutServiceTest.java:78-84 checks one reserveStock(ORD-100, [SKU-002x2, SKU-003x1]) and no confirm. The orderId is the same one persisted (:66) and emitted (:73). The stub resets per context (ServicesInMemoryConfig).
- NOTE T-06/T-07, stale event version: not resolved. Now confirmed; see the first SHOULD FIX below. The T-21 assertion passes for the wrong reason.
- NOTE T-06, UnsupportedOperationException: resolved by T-10 (S2).
- NOTE TestClock.NOW: resolved (OrdersCheckoutApiControllerTest.java:58).
- NOTE, flows package: not resolved. 4 flow tests are now in adapters.web and 1 in adapters.events. Still a NOTE.
- NOTE, spotless on OrdersCheckoutServiceTest (S4): T-21 reformatted the lines that were red (:158-180). Not verified, since spotless:check needs Maven.

MODEL AXIS
- SHOULD FIX  T-07/T-21  PlaceOrderFlowCompletedIntegrationTest.java:75-79, OrdersCheckoutServiceImpl.java:109-114  The emitted OrderConfirmed.version is still the version from before the confirm write, so the board's T-21 fix ("emit after flush if stale") was not made.
  - The test passes only because of a spurious UPDATE (next finding). failsafe-reports/TEST-...PlaceOrderFlowCompletedIntegrationTest.xml shows the order of events. The DTO from start-checkout has version 0. Then, inside confirmOrder, the findByOrderId auto-flush runs `insert` and then an `update` with no change, which bumps the version to 1. asOrderConfirmed then reads 1.
  - The confirm change itself is never flushed in the test, because the test transaction rolls it back.
  - At runtime the confirm transaction loads v(n), emits v(n), and commits v(n+1).
  - To settle it: compare OrderConfirmed.version with the row's version after commit (non-transactional, as in the Kafka IT). If it is stale, use saveAndFlush before mapping.
  - The ZDL (domain-model.zdl:177) does not define what version means. If it is not meant to be the post-write version, the person should say so and T-21 should drop that assertion.
- SHOULD FIX  T-07  core/domain/Order.java:56-64 (items, shippingAddress as SqlTypes.JSON; OrderItem and ShippingAddress have no equals)  Every flush of a loaded Order writes an UPDATE and bumps the version, even when nothing changed.
  - Evidence from the same run: OrderRepositoryIntegrationTest#findByOrderIdTest only reads, yet it logs 3 `update checkout_order ... where id=? and version=?`. saveTest logs insert and then update.
  - Effect on S1:
    - A PaymentAuthorized redelivery on CONFIRMED (ZFL 67-68 "no-op", ZDL 133) and rejectConfirmation both still write the row and bump the version.
    - Concurrent redeliveries can raise OptimisticLockException.
    - Event versions skip numbers.
    - The version assertion above is masked.
  - The domain entities are generated structure (AGENTS.md), so the fix location is the orchestrator's or the person's call: equals/hashCode through the model or generator, or a JSON type or mutability plan.
- NOTE  T-08/T-20  application.yml:57-65  asyncapi-client.yml:27-38 and 47-58 declares x-error-topics (3 retry topics plus a DLQ, address template orders_checkout.__.<topic>.<suffix>) for both consumers. None is wired, so a failing confirmOrder or cancelOrder gets the binder's default in-process retries and is then dropped. The T-20 report lists this as out of scope, and no board task covers it. It is tied to the unknown-orderId policy, which is with the person.
- NOTE  T-06/T-07/T-20  OrdersCheckoutServiceImpl.java:87-88, 113-114  Events go out through StreamBridge inside the @Transactional method, before the commit, and the boolean that send() returns is ignored.
  - If the commit fails (for example an optimistic lock), a phantom OrderCreated or OrderConfirmed is left behind.
  - The ZDL and the ADRs say nothing about an outbox. It needs a decision by the person, not a code fix now.
  - UNCONFIRMED: PlaceOrderFlowKafkaIntegrationTest.java:180-189 does GET CONFIRMED right after OrderConfirmed is polled, which can race the confirm commit. The window is small, and a repeated run would settle it.
- NOTE  T-20  application.yml:40-44  The keys are String, but the generated producer sends no key. Records are unkeyed and spread across partitions, so one order's OrderConfirmed and OrderCancelled are not ordered for consumers. The contract declares no key trait (asyncapi-message-traits kafkaKeyString is unused), so this is for the person.
- NOTE  T-07  EventsMapper.java:17  The MapStruct warning "asOrderConfirmed unmapped target property shippingAddressBuilder" is confirmed by reading.
  - The Avro OrderConfirmed.Builder has setShippingAddressBuilder (target/generated-sources/.../events/avro/OrderConfirmed.java:617).
  - It is harmless: EventsMapperImpl.java:65 sets shippingAddress, and the builder stays null, so build() uses the value.
  - It has been there since generation (0498a68). EventsMapper is generated-once: add `@Mapping(target = "shippingAddressBuilder", ignore = true)`.
  - The T-20 report's "no Unmapped warnings" is wrong. Compiling was not rerun (no Maven).
- Checked and correct at the head:
  - T-06: generator, then reserveStock, then totalAmount, CREATED and createdAt, persist, and only OrderCreated.
  - T-07: findByOrderId, then the CONFIRMED no-op, then confirmStockReservation drives the branch, then CONFIRMED and confirmedAt, then OrderConfirmed with items and shippingAddress (ZFL 155-164, ZDL 132-139, 174-193).
  - T-08: orderId is mapped to confirmOrder.
  - T-09: the in-process flow test.
  - T-20: the end-to-end Kafka IT. Its consumers are auto-startup false elsewhere (application-test.yml:14-17), so cached contexts do not steal records.
- Out of scope: none. Nothing was added that the models do not declare.
- Decisions with the person, not raised: the unknown-orderId policy, the source of confirmedAt, the hand-written inventory client, OrderCreated.amount as a double, the openapi 404, and the Avro wire format (T-23).

STANDARDS AXIS
- NOTE  adapters/web/PlaceOrderFlow*IntegrationTest.java, adapters/events/PlaceOrderFlowKafkaIntegrationTest.java  The flow tests are split across adapter packages, next to the generated tests. A `flows` package would group them. Carried over from the previous review.
- NOTE  PlaceOrderFlowKafkaIntegrationTest.java:117-119  `assertTrue(!...)`: the other tests use assertFalse (PlaceOrderFlowLateAuthorizationIntegrationTest.java:84).
- AGENTS.md:
  - 0c5896a (T-21) and fea7f64 (T-20) are unsigned (%G? = N) and carry the AI-Agent, AI-Model and AI-Orchestrator trailers.
  - Merges 43cb1de and e1720bd carry AI-Orchestrator.
- Conventions:
  - SchemaRegistryConfiguration is in config and uses constructor-style @Bean.
  - The test switch is a property (@ConditionalOnProperty), not mocks, in line with the overlay's "prefer in-memory over Mockito".
  - The Kafka adapter is tested with Testcontainers (overlay rule).
  - The recording stub follows the style of the existing stub.

PROPOSALS: none. The version and JSON-dirty findings are code or generator issues. If the entities cannot be fixed without regenerating, the person decides.
NOT CHECKED:
- The suite was not rerun (as agreed). Totals come from the reports.
- spotless:check, and the compile warnings from a compile run (no Maven).
- Interop with real payments and inventory (T-23).
- Runtime version values against a committed row (see the first SHOULD FIX).
