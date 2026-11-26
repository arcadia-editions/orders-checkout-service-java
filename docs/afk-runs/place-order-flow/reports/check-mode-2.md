# Check mode 2: place-order-flow, OrdersCheckout context

skill: zenwave-tasks-from-code-and-models (check mode)
checkout: .zenwave/work/suite @ 2c96a26 (clean tree)
models: ZFL arcadia-editions-architecture/business-flows/place-order-flow.zfl, ZDL orders-checkout-api/domain-model.zdl (0.2.0, 9fd0cc3), inventory ZDL catalog-inventory-api/domain-model.zdl (fa4d30a). Model repos clean except arcadia-editions-architecture/zenwave-architecture.yml (M, not a flow model).
suite evidence: logs/suite-2c96a26.log, surefire 36/0, failsafe 25/0 (per prompt; log has no surefire summary lines, but shows PlaceOrderFlowKafkaIntegrationTest, stock-released-event-v1-in-0 with 1 subscriber, and traffic on order-cancelled, stock-unavailable, order-confirmation-rejected topics).

Abbreviations as in check-mode.md (`Impl`, `EM`, `SvcT`, `ApiIT`, `CtlT`, `Flow*IT`, `KafkaIT`).

## Previous gaps

| Gap | Closed by | Evidence | Verdict |
|---|---|---|---|
| GAP 1 Kafka wiring of cancelled, stockGone, late-auth | T-27 (630d3c2) | KafkaIT:72 starts stock-released-event-v1-in-0; :235 stockReleasedOnKafkaCancelsTheOrder (StockReleased PAYMENT_DECLINED on catalog-inventory topic -> OrderCancelled on its topic, reason, customerId/Email, cancelledAt, version <= row, GET CANCELLED); :322 SKU-004 -> 409, no row, 1 StockUnavailable on topic, 0 OrderCreated; :390 ORD-006 (C-06) PaymentAuthorized -> 1 OrderConfirmationRejected ORDER_CANCELLED on topic, 0 OrderConfirmed, fixture row unchanged | closed |
| GAP 2 OrderCreated payer fields | T-28 (831d4a8) | SvcT:77-79 customerId CUST-100, customerEmail, paymentMethodId PM-100; FlowCompletedIT:77-80 CUS-100, reader@example.com, PM-100 | closed |

## Step 2: models hold

- Lint: `lint-zdl.sh --strict` (report dir in scratchpad, not the checkout) on both ZDLs: OK, OK, exit 0.
- Identities unchanged: zfl:10 commands = zdl:130, 139, 144; events zdl:151, 163, 175, 196, 208; `@compensates(startOrderCheckout)` zfl:305 -> cancelOrder at zfl:306.
- Proposals: P-01, P-02, both accepted, none open. `grep TODO src/main`: none.

## Step 3: paths

Same as check-mode.md: S1 completed, S2 stockGone, S3 orderCancelled (5 causes), S4 late authorization (stock-not-held, order-cancelled). notificationFailed: other context.

## Step 4: coverage

src/main and src/test/resources unchanged since 43f010d (previous check): only 3 test files changed (T-27, T-28). Table of check-mode.md stands; changed rows:

| Model element | Code | Test |
|---|---|---|
| zfl:91 / zdl:150-160 OrderCreated (all fields authorizePayment needs, zfl:114-122) | Impl:87-88, EM:14-15 | SvcT:68-79 (orderId, amount, currency, version, customerId, customerEmail, paymentMethodId); FlowCompletedIT:76-80; KafkaIT:180-182 |
| zfl:306, zdl:142 `@asyncapi stock-released-event-v1` listener binding | StockReleasedConsumerService.java:47-52; application.yml stock-released-event-v1-in-0 | StockReleasedConsumerServiceTest:34; KafkaIT:235 (over Kafka, binding started) |
| Published topics zdl:150, 162, 174, 195, 207 | application.yml 5 *-out bindings | all 5 proven on Kafka: KafkaIT:180 (created), :193 (confirmed), :282 (cancelled), :366 (stock-unavailable), :407 (confirmation-rejected); schema subjects asserted |
| everything else (start, reserveStock, transitions, guards, redelivery no-ops, confirmStockReservation, outcomes, getOrder, test data C-01..C-10) | unchanged | unchanged, as check-mode.md rows 32-51 |

Notes (not gaps): stock-not-held (C-05) not repeated on Kafka; same binding and topic as C-06, which is proven. Board S6 `review: [ ]` still open (orchestrator's step, not a model gap).

Known with the person, not listed: T-23 Avro wire format, unknown-orderId policy, x-error-topics/DLQ, events before commit, unkeyed records, extra UPDATE on loaded Orders, 404 not in openapi, amount as Avro double, confirmedAt source, hand-written inventory client.

## GAPS

none
