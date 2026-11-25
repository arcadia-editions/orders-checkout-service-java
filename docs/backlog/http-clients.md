# HTTP clients for synchronous calls

Manual task, not on the flow board (`docs/tasks.md`). Do it before implementing the flow steps that call Catalog Inventory.

## Why

`place-order-flow.zfl` has steps where this service calls Catalog Inventory synchronously (`call ...`), and it waits
for the result:

- `startOrderCheckout` calls `reserveStock` (`POST /inventory/reservations/reserve`): `StockReservedResult` or `StockUnavailableResult`.
- `confirmOrder` calls `confirmStockReservation` (`POST /inventory/reservations/confirm`): `StockReservationConfirmedResult` or `StockReservationNotHeldResult`.

Today these calls are `TODO`s (or don't exist yet, before generation). The contract is
[`catalog-inventory-api/openapi.yml`](https://github.com/arcadia-editions/catalog-inventory-api/blob/main/openapi.yml).

## What to do

1. **Generate the client interfaces.** Add a second `openapi-generator-maven-plugin` execution to `pom.xml`
   (id `generate-catalog-inventory-client`), next to the existing one:
   - properties: `<catalog-inventory.openapi.inputSpec>https://raw.githubusercontent.com/arcadia-editions/catalog-inventory-api/refs/heads/main/openapi.yml</catalog-inventory.openapi.inputSpec>`,
     `<catalogInventoryClientPackage>${basePackage}.clients.inventory</catalogInventoryClientPackage>`;
   - `generatorName` `spring`, `library` `spring-http-interface` (Spring `@HttpExchange` interfaces, no implementation);
   - `apiPackage` `${catalogInventoryClientPackage}`, `modelPackage` `${catalogInventoryClientPackage}.dtos`, `modelNameSuffix` `DTO`;
   - `generateSupportingFiles` `false`, `useTags` `true`, `openApiNullable` `false`, `documentationProvider` `none`,
     `useJakartaEe` `true`, same `typeMappings` as the server execution;
   - `operationIds` / `apisToGenerate` limited to what this service calls: `reserveStock`, `confirmStockReservation`.

   Generated into `target/` like the rest: never edit.

2. **Build the client from the interfaces.** One `@Configuration` in `config` (e.g. `CatalogInventoryClientConfiguration`)
   that binds `arcadia.clients.catalog-inventory.*` (already in `application.yml`) and exposes the generated interface as a
   bean:

   ```java
   RestClient restClient = RestClient.builder()
       .baseUrl(props.baseUrl())                      // ${CATALOG_INVENTORY_URL:http://localhost:8102/api}
       .requestInterceptor(new BasicAuthenticationInterceptor(props.username(), props.password()))
       .build();
   HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
   return factory.createClient(InventoryApi (the name depends on the tag).class);
   ```

   (Or Spring Boot 4 `@ImportHttpServices` + `spring.http.serviceclient.catalog-inventory.base-url`, if preferred; then move
   the properties.)

3. **Adapter for the outbound port.** The core calls Catalog Inventory through an outbound port in `core.outbound`
   (the generated one, or one the board names). An adapter in `adapters` implements it with the generated interface
   and maps DTOs with MapStruct. Map each response the flow names (`StockReservedResult`, `StockUnavailableResult`, `StockReservationConfirmedResult`, `StockReservationNotHeldResult`) to the port's result; HTTP errors
   other than those are technical failures.

4. **Tests.** The adapter is tested against a stub of the contract (WireMock, or MockRestServiceServer). The core
   tests keep using an in-memory fake of the port.

## Runtime

| Property | Env var | Local default | docker-compose |
|---|---|---|---|
| `arcadia.clients.catalog-inventory.base-url` | `CATALOG_INVENTORY_URL` | `http://localhost:8102/api` | `http://catalog-inventory-service:8102/api` |
| `arcadia.clients.catalog-inventory.username` / `password` | `CATALOG_INVENTORY_USERNAME` / `CATALOG_INVENTORY_PASSWORD` | `admin` / `password` | same |

Ports of every service: `arcadia-editions-microservices-compose/README.md`.
