# Transactional outbox with Spring Modulith in the AsyncAPI generator

Manual task, not on the flow board (`docs/tasks.md`).

## Why

`OrdersCheckoutServiceImpl` saves the order and sends the event in the same method, but the event goes to Kafka
before the transaction commits. A failed commit leaves an event for an order that doesn't exist; a crash after the
commit and before the send loses the event. Every command of place-order-flow (startOrderCheckout, confirmOrder,
cancelOrder) has this window.

## What to do

1. `pom.xml`, both `zenwave-sdk-maven-plugin` executions of `AsyncAPIGenerator` (`generate-asyncapi`,
   `generate-asyncapi-client`): turn on the transactional outbox option with Spring Modulith (check the generator's
   option name and value in the ZenWave SDK docs for `${zenwave.version}`), so the generated producer publishes
   through Modulith's event publication registry instead of sending directly.
2. Dependencies: `spring-modulith-starter-jpa` (publication registry in PostgreSQL) and
   `spring-modulith-events-kafka` (externalization), versions from the Spring Modulith BOM for Spring Boot 4.1.
3. The `event_publication` table: let Modulith create it (`spring.modulith.events.jdbc.schema-initialization.enabled`)
   or add it to the schema the project uses. `spring.modulith.events.republish-outstanding-events-on-restart: true`.
4. Regenerate (person), then check the generated-once code still compiles: `DefaultOrdersCheckoutEventsProducer`
   names and the test `InMemory` producer.
5. Tests: an event is not on Kafka when the transaction rolls back; it arrives after commit
   (`PlaceOrderFlowKafkaIntegrationTest`). Revisit the `saveAndFlush` before mapping events (T-25): with events
   sent after commit, the version can be read after flush anyway.
6. An ADR (`docs/adr/`) for the decision, since it changes delivery semantics (at-least-once, after commit).

HITL: it changes the generator config and regenerates code, which AFK workers never do.
