# Orders Checkout Service (Java): ZenWave SDD overlay

What every zenwave-sdd skill and agent needs to know about this project: where the models are, its shape, its
commands, and where its test data is. `AGENTS.md` points here.

The placeholders marked `<after generation: ...>` and `<after test data: ...>` are filled in by the skill that
produces the fact (`zenwave-test-data` / `zenwave-test-scenarios` the test data row; `zenwave-gate-check` suggests the
full-suite duration).

## Where things are

Paths are relative to this repository's root. The models live in sibling repositories that must be cloned next to
this one: `../arcadia-editions-architecture` (flows) and `../orders-checkout-api` (this context's ZDL and contracts).
The flow also references the other contexts' ZDLs: `../catalog-inventory-api`, `../payments-processing-api`,
`../fulfillment-shipping-api`, `../notifications-consumer-api`.

| What | Path |
|---|---|
| Business flows (ZFL) | `../arcadia-editions-architecture/business-flows/*.zfl` (today: `place-order-flow.zfl`) |
| Domain models (ZDL), one per bounded context | `../orders-checkout-api/domain-model.zdl` (this service); other contexts: `../<context>-api/domain-model.zdl` |
| API contracts | `../orders-checkout-api/openapi.yml`, `../orders-checkout-api/asyncapi.yml` (published events), `../orders-checkout-api/asyncapi-client.yml` (consumed events), Avro schemas in `../orders-checkout-api/avro/` |
| Glossary and ADRs | `GLOSSARY.md`, `docs/adr/` |
| Domain summary | `docs/domain.md` |
| Board of the flow in progress | `docs/tasks.md` (archived boards in `docs/tasks/`) |
| Model gap proposals | `docs/proposals/` |
| Test data and scenario matrix | `src/test/resources/data.sql` (header: one example per entity, chain `C-01`) and `src/test/resources/data/jpa/<Entity>/<n>.json` |
| Generation script | `zenwave-scripts.zw` |

**The models are read from raw GitHub URLs, not a packaged artifact.** `zenwave-scripts.zw` (`zdlFile`,
`openapiFile`) and `pom.xml` (`openapi.inputSpec`, `asyncapi.inputSpec`, `asyncapi.client.inputSpec`, the
`*.avro.imports`) point at `https://raw.githubusercontent.com/arcadia-editions/orders-checkout-api/refs/heads/main/...`
(client Avro imports also at `catalog-inventory-api`). The generated code reflects `main` on GitHub, not the local
clone.

- staleness check (prints nothing when the local clone matches what the build reads):

  ```bash
  R=https://raw.githubusercontent.com/arcadia-editions/orders-checkout-api/refs/heads/main
  for f in domain-model.zdl openapi.yml asyncapi.yml asyncapi-client.yml; do
    curl -fsSL "$R/$f" | diff -q --strip-trailing-cr - "../orders-checkout-api/$f" >/dev/null || echo "stale: $f"
  done
  ```

- publish a model change: commit and push it to `main` of `orders-checkout-api` (and the sibling repositories it
  touches); then regenerate here.

**The ZFL's `@zdl(...)` paths** (`../../<context>-api/domain-model.zdl`, relative to `business-flows/`) resolve to
the sibling clones above; no mapping needed. This service owns `orders-checkout-api/domain-model.zdl` only.

## Shape

- Deployment: **microservices** (one bounded context per repository; Kafka and AsyncAPI with Avro payloads, Schema
  Registry; ZDL consumers use `@asyncapi`).
- Code already in place: yes (`zenwave-tasks-from-code-and-models`). Generated with ZenWave SDK: yes (2.8.1, commit
  `0498a68`); service methods and event listeners are still the generated `TODO`s.
- Stack: Java 25, Spring Boot 4.1.2, Spring Cloud Stream (Kafka), MapStruct, ArchUnit, Maven (no wrapper: `mvn`).
- Persistence: `jpa` (PostgreSQL; Testcontainers via docker-compose in tests).
- Organization prefix and packages: `com.arcadiaeditions` (base package `com.arcadiaeditions.orders.checkout`),
  `CleanHexagonalProjectLayout`: `core.domain`, `core.application`, `core.inbound`, `core.outbound`,
  `adapters.web`, `infrastructure` (JPA), `config`; generated AsyncAPI SDK outside the hexagon in
  `asyncapi.provider` (`asyncapi.yml`) and `asyncapi.client` (`asyncapi-client.yml`), Avro models in `events.avro`.

## Commands

```bash
# JDK and tools: Java 25 and Maven on PATH (SDKMAN), JBang with the `zw` alias, Docker
export JAVA_HOME=~/.sdkman/candidates/java/current
# regenerate after a model change (the person runs it; never the orchestrator or its workers):
# the 3 plugin blocks of zenwave-scripts.zw, in order (BackendApplicationDefaultPlugin, OpenAPIControllersPlugin,
# SpringWebTestClientPlugin); the IDE play button shows each `jbang zw -p <Plugin> ...` command. Never --force.
# Then: mvn -DskipTests clean test-compile (runs the OpenAPI / AsyncAPI Maven generators) and review git diff.
# compile, and collect MapStruct warnings
mkdir -p target && mvn -q -DskipTests clean compile 2>&1 | tee target/compile.log
# focused tests of a task (the implementer runs only these)
mvn -q test -Dtest='<TestClass>'
# full suite with integration tests and containers (once per slice review)
# <after generation: full suite takes about N minutes, needs Docker (Testcontainers starts the docker-compose PostgreSQL / Kafka / Schema Registry)>
mvn -q verify
# lint the models
bash .zenwave/bin/lint-zdl.sh --strict ../orders-checkout-api/domain-model.zdl
# export the Hibernate DDL (JPA)
bash .claude/skills/zenwave-test-data/scripts/export-ddl.sh
```

The orchestrator gives the workers the focused command (with the task's `tests:`) and the reviewer the full one.

## Project rules

- Naming conventions (ids, channels, topics): `.claude/skills/zenwave-grill-models/references/zenwave-languages/asyncapi-naming-conventions.md`;
  shared AsyncAPI traits, bindings and error topics in `../api-contract-commons/`.
- Tests: `*Test` run with Surefire (`mvn test`), `*IT` / `*IntegrationTest` with Failsafe (`mvn verify`). Prefer
  in-memory repositories and vertical tests over Mockito; adapters to external resources are tested with
  Testcontainers.
- Generated at build time into `target/` (never edit): OpenAPI interfaces and `*DTO`s, the AsyncAPI SDK.
- Format with `mvn spotless:apply`.
- Decisions already taken that the models don't show: none recorded yet (`docs/adr/`). For a reference
  implementation of the same flow, see `../arcadia-editions-modulith-service-java/docs/zenwave-sdd-overlay.md`
  (Project rules) and its ADRs.
