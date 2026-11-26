# ADR 0002: Avro on Kafka uses the Confluent serializer

status: accepted
date: 2026-11-26
decided by: the person (T-23, place-order-flow)

## Context
The contracts put Avro schemas in the Confluent Schema Registry (`api-contract-commons/asyncapi-master.yml`,
`confluentSchemaRegistry`). Spring Cloud Stream's `application/*+avro` converter writes its own framing and
subjects, which the Confluent `KafkaAvroDeserializer` of payments-processing (its T-31) and catalog-inventory can't
read, and the reverse. The service must run in production and locally with docker-compose (cp-schema-registry).

## Decision
- Values are serialized with `io.confluent.kafka.serializers.KafkaAvroSerializer` and `KafkaAvroDeserializer`
  (Confluent magic byte + schema id), `specific.avro.reader: true`, default `TopicNameStrategy` subjects
  (`<topic>-value`).
- Spring Cloud Stream bindings use native encoding/decoding; the `application/*+avro` converter and its
  `SchemaRegistryClient` bean go away.
- `schema.registry.url` comes from `SCHEMA_REGISTRY_URL` (docker-compose: `http://localhost:8081`).
- `pom.xml` gets `io.confluent:kafka-avro-serializer` (same version as payments-processing, 8.0.0) and the Confluent
  Maven repository `https://packages.confluent.io/maven/`.

## Consequences
- Wire compatible with every other Arcadia service and with Confluent tooling (kafka-avro-console-consumer, ksql).
- Schema auto-registration stays on locally; production should pre-register from the contracts
  (`auto.register.schemas: false`), a deployment setting.
- A dependency outside Maven Central.
