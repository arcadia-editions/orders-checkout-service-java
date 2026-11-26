# ADR 0001: orderId is a UUIDv7 the service generates

status: accepted
date: 2026-11-25
decided by: the person (T-04, place-order-flow)

## Context
`Order` has two identities ([domain-model.zdl:58](../../../orders-checkout-api/domain-model.zdl#L58)): `id`, the
Long the database generates on insert, local to this service; and `orderId`, `String required unique`, the business
key every other context and every event correlates on. The models do not say who generates `orderId` or its format.
`startOrderCheckout` calls `reserveStock` before persisting, and StockUnavailable carries an `orderId` for an order
that is never persisted, so `orderId` must exist before the row.

## Decision
- Keep both `Order.id` (database surrogate) and `Order.orderId` (business key). No model change.
- The service generates `orderId` at the start of `startOrderCheckout`, before `reserveStock`: a UUIDv7 string
  (time-ordered, lowercase canonical form), never sent by the client.
- Generation goes through an injectable generator (`OrderIdGenerator`, a supplier of String) in `core`: UUIDv7 in
  main, a deterministic stub in tests (`ORD-<n>`, like the fixtures).

## Consequences
- No change to ZDL, contracts or test data; no new baseline.
- `GET /orders/{id}` still reads by the numeric `id`, while events and other contexts use `orderId`. Exposing a read
  by `orderId` is a model change, left for a later proposal.
- Not idempotent on client retries: a retried POST creates a new order. Revisit with a client idempotency key if needed.
