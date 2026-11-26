VERDICT: PASS
SLICE: S0 (fix round)   RANGE: db3ceda..2fb0cc6 (checkout .zenwave/work/review-S0b at 2fb0cc6, tree clean before and after)
SUITE: cd .zenwave/work/review-S0b && mvn -q verify -Dmaven.test.failure.ignore=true → 22:18:32 to 22:26:24 (about 8 min; the wait for free ports is not included), exit 0.
  Before the run: docker ps showed ports 5432/9092/8081/2181 held by containers of another project (compose project i7mafjmlvnwt, payments-processing-service-java/.zenwave/work/T-07, plus a Testcontainers ryuk/socat). I did not start then. I did the static review first and checked again: the ports were free, so I started the suite. A run of that project while ours was going would have collided.
  surefire 26 run, 0 red: ArchitectureTest 2/2 green (T-18), ClockConfigurationTest 1, OrdersCheckoutServiceTest 6, UuidV7OrderIdGeneratorTest 2, InventoryClientRestAdapterTest 8, InventoryClientStubTest 4, OrderRepositoryInMemoryTest 1, OrdersCheckoutApiControllerTest 2.
  failsafe 11 run, 3 red, exactly the expected set: OrdersCheckoutApiIntegrationTest.testStartOrderCheckout_201 (400 vs 201, T-09), testStartOrderCheckout_409 (400 vs 409, T-10), testGetOrder_400 (404 vs 400, T-17). OrderRepositoryIntegrationTest 6/6 green. Nothing else red, nothing flaky (single run).
BASELINE: untouched (git diff --stat db3ceda..2fb0cc6 over *.zfl, *.zdl, *openapi*.yml, *asyncapi*.yml, src/test/resources/data* is empty).

PREVIOUS FINDINGS (review-S0.md)
- SHOULD FIX T-02 tests comparing a constant with itself: resolved. serviceSeesTheFixedTestInstant and systemClockBeanIsUtc are removed from OrdersCheckoutServiceTest (6525fd0). testProfilePrefersTheFixedClock moved to src/test/.../config/ClockConfigurationTest.java:11-19 and checks real wiring (the @Primary fixed clock wins under profile test). It is green.
- SHOULD FIX T-02 OrdersCheckoutServiceImpl.clock() accessor: resolved. Removed. `grep clock()` matches only the ClockConfiguration bean.
- NOTE T-05 lenient mapping: resolved. InventoryClientRestAdapter.java:73-76, 92-94 and 105-108 now throw IllegalStateException when the body for RESERVED, UNAVAILABLE or CONFIRMED is missing. Covered by InventoryClientRestAdapterTest#reserveStockReservedWithoutBodyIsAContractViolation, #reserveStockUnavailableWithoutBodyIsAContractViolation and #confirmStockReservationConfirmedWithoutBodyIsAContractViolation (green). NOT_HELD still falls back (see below).
- NOTE T-05 stub not wired in Spring test profile: not resolved, as expected. The stub is still only @Primary in ServicesInMemoryConfig (profile in-memory, not booted). testStartOrderCheckout_201/_409 are red and belong to T-09/T-10.
- NOTE T-03 findByOrderId: no change needed. findByOrderIdTest is green.
- NOTE T-04 OrderIdGenerator missing: resolved (by T-06). core/outbound/ids/OrderIdGenerator.java, UuidV7OrderIdGenerator in main, and OrderIdGeneratorStub (ORD-100..) in tests, as ADR 0001 says. UuidV7OrderIdGeneratorTest (version 7, variant 2, lowercase canonical, embedded millis, time order with a 5 ms gap) is green and does not depend on timing.
- NOTE board T-05 without done: resolved. T-05 has `done: claude-code claude-opus`.
- SHOULD FIX standards, hand-written inventory client: not raised again. This decision is still with the person (docs/backlog/http-clients.md).
- NOTE T-01 reformatting churn: unchanged. Harmless.
- NOTE stale "T-02" comment and literal instant: resolved. OrderRepositoryIntegrationTest.java:86 uses TestClock.NOW and the comment is gone. OrdersCheckoutServiceTest has no literal left.
- NOTE mixed assertEquals styles: resolved. OrdersCheckoutServiceTest.getOrderTest uses the static import only.

MODEL AXIS (new)
- NOTE  T-19 InventoryClientRestAdapter.java:80-84  NOT_HELD with no `notHeld` body still yields StockReservationNotHeldResult(requested orderId), unlike the other three outcomes, which now throw. This is defensible: catalog-inventory openapi.yml:373-378 gives StockReservationNotHeldResult only an optional orderId, so the outcome alone carries the information. No change needed.
- NOTE  T-18 ArchitectureTest.java:47-48, 53, 62-65  matches the person's option 1. New optional layers AsyncApiSdk (..asyncapi..) and EventsAvro (..events.avro..). CoreApplication may reach both. Adapters, Domain, CoreInbound and CoreOutbound are not widened: their rules are unchanged, and the SDK and Avro layers may be accessed only by CoreApplication, Adapters and the SDK. No other rule is weakened (compared with db3ceda). Both ArchTest rules are green.
- Out of scope: none. T-18 and T-19 add no field, endpoint or event.

STANDARDS AXIS (new)
- NOTE  src/test/.../ArchitectureTest.java  fails `mvn spotless:check`, as it already did at the baseline: it is one of the 11 pre-existing failing generated files, all outside S0's changes (`// formatter:off` hand-aligned). T-18 kept the file's own style, so this is not a regression. Every other file touched by T-18/T-19 is spotless-clean.
- NOTE  ArchitectureTest.java:25 (javadoc)  "Adapters: may access Inbound Interfaces, Domain, the generated AsyncAPI SDK and Avro models" describes a constraint no rule enforces: there is no `whereLayer("Adapters").mayOnlyAccessLayers(...)`, now or at the baseline. Cosmetic.
- NOTE  docs/zenwave-sdd-overlay.md "Where things are", test data row  still says the fixed Clock is "not yet in the code". That has been stale since T-02. Only the person or the orchestrator edits the overlay.
- NOTE  docs/tasks.md  T-19 is still `[/]` with no `done:` line although merged (2fb0cc6). The orchestrator owns the board.
- Followed: the AFK commits for T-18 and T-19 carry the AI-Agent, AI-Model and AI-Orchestrator trailers. No Mockito added (the static import in OrdersCheckoutServiceTest is generated, present at db3ceda). No generated-at-build file edited. No model, contract or test-data edits.

PROPOSALS: none written.
NOT CHECKED: the adapter against a real catalog-inventory instance (only MockRestServiceServer); S1 tasks T-06/T-07/T-08 (out of scope, they only ran in the suite).
