# Spring Cloud Stream binding config in application.yml

Manual task, not on the flow board (`docs/tasks.md`).

## Why

The bindings in [`src/main/resources/application.yml`](../../src/main/resources/application.yml)
(`spring.cloud.function.definition`, `spring.cloud.stream.bindings.*`: destination, group, native encoding/decoding,
`auto-startup`) were written by hand in the AFK loop (T-20, T-23). They repeat what the contracts already say:
channel addresses and operations in `orders-checkout-api/asyncapi.yml` and `asyncapi-client.yml`, the group in
`x-application-bindings.x-groupId`. A new channel, or a renamed topic, in the contracts doesn't reach the config:
the build stays green and the binding silently publishes to the wrong topic or never consumes.

## What to do

1. Decide where the source of truth is: generated from the AsyncAPI contracts (ZenWave generator, or a generated
   `application-asyncapi.yml` imported with `spring.config.import`), or kept by hand with a check.
2. If generated: binding names (`<operation>-in-0`, StreamBridge names of `DefaultOrdersCheckoutEventsProducer`),
   destinations from channel addresses, group from `x-groupId`, native encoding/decoding for Avro (ADR 0002). Remove
   the hand-written block.
3. If by hand: a test that loads both contracts and asserts every send/receive operation has a binding with the
   channel address as destination.
4. Same for the retry and dead-letter topics (`x-error-topics` in `asyncapi-client.yml`), not wired today.

Generated structure is not hand-edited (AGENTS.md): this is a HITL change to the generator config.
