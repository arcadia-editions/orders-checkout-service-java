-- Scenario test data. Regenerate with $zenwave-test-scenarios (place-order-flow).
-- DDL exported 2026-11-25 to target/ddl/schema.sql (models: orders-checkout-api main). Only aggregate: Order (checkout_order).
-- Fixed test "now" (fixed Clock, still to be added by a board task): 2026-10-14T10:00:00Z.
-- JSON mirrors: src/test/resources/data/jpa/Order/<k>.json, k = row position, id = 1 + (k-1)*50.
--
-- SCENARIO MATRIX: chain | business ids | Order state (upstream state implied) | used by
-- C-01 | ORD-001, CUST-001, PM-001, SKU-001x1 | CONFIRMED (stock confirmed, payment authorized) | getOrder CONFIRMED; confirmOrder redelivery no-op; flow completed (orders side); repository tests findById(1)
-- C-02 | ORD-002, SKU-002x2 + SKU-003x1 | CREATED (stock reserved, payment authorized) | confirmOrder confirmed (PaymentAuthorized); multiplicity (2 lines, total 99.97); getOrder CREATED
-- C-03 | ORD-003, SKU-002x1 | CREATED (stock just released) | cancelOrder from CREATED (StockReleased PAYMENT_DECLINED / PAYMENT_RETRY_EXHAUSTED / RESERVATION_EXPIRED)
-- C-04 | ORD-004, SKU-002x1 | CONFIRMED (fulfillment or capture failed, payment voided, stock released) | cancelOrder from CONFIRMED (StockReleased PAYMENT_VOIDED)
-- C-05 | ORD-005, SKU-002x1 | CREATED (stock released by the timer, late authorization) | confirmOrder stock-not-held (stub-driven: inventory answers NOT_HELD -> OrderConfirmationRejected STOCK_NOT_HELD, P-01 A)
-- C-06 | ORD-006, SKU-002x1 | CANCELLED RESERVATION_EXPIRED (late authorization) | confirmOrder order-cancelled (OrderConfirmationRejected ORDER_CANCELLED, P-01 A); cancelOrder redelivery no-op; outcome cancelled by expiry; getOrder CANCELLED
-- C-07 | ORD-007, SKU-002x1 | CANCELLED PAYMENT_DECLINED | outcome cancelled by decline; cancelOrder redelivery
-- C-08 | ORD-008, SKU-002x1 | CANCELLED PAYMENT_RETRY_EXHAUSTED | outcome cancelled by retries exhausted
-- C-09 | ORD-009, SKU-002x1 | CANCELLED PAYMENT_VOIDED (was CONFIRMED) | outcome cancelled after fulfillment / capture failure
-- C-10 | ORD-010, SKU-002x1 | CANCELLED PAYMENT_VOID_FAILED (was CONFIRMED) | outcome cancelled, void refused (P-02 A); getOrder shows the hold may still show
--
-- COVERAGE (need -> chain, or why no row)
-- Lifecycle: confirmOrder from CREATED -> C-02; cancelOrder from CREATED -> C-03, from CONFIRMED -> C-04
-- Branches: startOrderCheckout OrderCreated | StockUnavailable -> no row, stub-driven (inventory reserveStock decides; the order is persisted only on OrderCreated);
--   confirmOrder confirmed -> C-02; stock-not-held -> C-05 (stub-driven); order-cancelled -> C-06
-- Outcomes: completed -> C-01; cancelled by decline C-07, retries exhausted C-08, voided C-09, expiry C-06; void refused C-10;
--   stockGone -> no row (no order persisted); notificationFailed -> no row (notifications context)
-- Multiplicity: items minlength(1) -> C-02 (2 lines)
-- Time: the 10-minute reservation timer is owned by catalog-inventory (expireReservation); Order has no deadline field -> no row here
-- Redelivery: confirmOrder on CONFIRMED -> C-01; cancelOrder on CANCELLED -> C-06/C-07; startOrderCheckout is HTTP (no redelivery)
-- Read side (GET /orders/{id}): CREATED -> C-02 (id 51), CONFIRMED -> C-01 (id 1), CANCELLED -> C-06 (id 251); not found -> any id not listed (e.g. 999)
--
-- ASSUMPTIONS: assumption | value used | open question
-- cancellation_reason holds the StockReleaseReason name from CancelOrderInput.reason | PAYMENT_DECLINED, ... | P-02 A (PAYMENT_VOID_FAILED added)
-- Prices accepted as sent by the client; totalAmount = sum(quantity * unitPrice) | 49.99 / 29.99 / 99.97 | -
-- Stock-unavailable API tests drive the inventory stub, not a row (e.g. SKU-004 has zero stock in catalog-inventory fixtures) | SKU-004 | stub contract
-- confirmedAt / cancelledAt are set by confirmOrder / cancelOrder; a CONFIRMED order cancelled later keeps confirmedAt (C-09) | as listed | -
-- Timestamps: ORD-001 keeps 2026-09-26; ORD-00n created 2026-10-14T09:50:0n, later steps +30..+80 s; ORD-006 created 09:40:06 (10-minute timer fired 09:50:06)

-- Orders Checkout
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-001', 'CONFIRMED', 'CUST-001', 'customer@example.com', 'PM-001',
        '[{"sku":"SKU-001","quantity":1,"unitPrice":49.99}]'::jsonb,
        '{"name":"Arcadia Customer 1","line1":"1 Book Lane","line2":null,"city":"Madrid","region":null,"postalCode":"28001","country":"ES"}'::jsonb,
        49.99, 'EUR', '2026-09-26T13:00:00Z', '2026-09-26T13:05:00Z', NULL, NULL); -- C-01
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-002', 'CREATED', 'CUST-002', 'customer002@example.com', 'PM-002',
        '[{"sku":"SKU-002","quantity":2,"unitPrice":29.99},{"sku":"SKU-003","quantity":1,"unitPrice":39.99}]'::jsonb,
        '{"name":"Arcadia Customer 2","line1":"2 Book Lane","line2":null,"city":"Madrid","region":null,"postalCode":"28002","country":"ES"}'::jsonb,
        99.97, 'EUR', '2026-10-14T09:50:02Z', NULL, NULL, NULL); -- C-02
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-003', 'CREATED', 'CUST-003', 'customer003@example.com', 'PM-003',
        '[{"sku":"SKU-002","quantity":1,"unitPrice":29.99}]'::jsonb,
        '{"name":"Arcadia Customer 3","line1":"3 Book Lane","line2":null,"city":"Madrid","region":null,"postalCode":"28003","country":"ES"}'::jsonb,
        29.99, 'EUR', '2026-10-14T09:50:03Z', NULL, NULL, NULL); -- C-03
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-004', 'CONFIRMED', 'CUST-004', 'customer004@example.com', 'PM-004',
        '[{"sku":"SKU-002","quantity":1,"unitPrice":29.99}]'::jsonb,
        '{"name":"Arcadia Customer 4","line1":"4 Book Lane","line2":null,"city":"Madrid","region":null,"postalCode":"28004","country":"ES"}'::jsonb,
        29.99, 'EUR', '2026-10-14T09:50:04Z', '2026-10-14T09:50:34Z', NULL, NULL); -- C-04
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-005', 'CREATED', 'CUST-005', 'customer005@example.com', 'PM-005',
        '[{"sku":"SKU-002","quantity":1,"unitPrice":29.99}]'::jsonb,
        '{"name":"Arcadia Customer 5","line1":"5 Book Lane","line2":null,"city":"Madrid","region":null,"postalCode":"28005","country":"ES"}'::jsonb,
        29.99, 'EUR', '2026-10-14T09:50:05Z', NULL, NULL, NULL); -- C-05
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-006', 'CANCELLED', 'CUST-006', 'customer006@example.com', 'PM-006',
        '[{"sku":"SKU-002","quantity":1,"unitPrice":29.99}]'::jsonb,
        '{"name":"Arcadia Customer 6","line1":"6 Book Lane","line2":null,"city":"Madrid","region":null,"postalCode":"28006","country":"ES"}'::jsonb,
        29.99, 'EUR', '2026-10-14T09:40:06Z', NULL, '2026-10-14T09:50:06Z', 'RESERVATION_EXPIRED'); -- C-06
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-007', 'CANCELLED', 'CUST-007', 'customer007@example.com', 'PM-007',
        '[{"sku":"SKU-002","quantity":1,"unitPrice":29.99}]'::jsonb,
        '{"name":"Arcadia Customer 7","line1":"7 Book Lane","line2":null,"city":"Madrid","region":null,"postalCode":"28007","country":"ES"}'::jsonb,
        29.99, 'EUR', '2026-10-14T09:50:07Z', NULL, '2026-10-14T09:51:07Z', 'PAYMENT_DECLINED'); -- C-07
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-008', 'CANCELLED', 'CUST-008', 'customer008@example.com', 'PM-008',
        '[{"sku":"SKU-002","quantity":1,"unitPrice":29.99}]'::jsonb,
        '{"name":"Arcadia Customer 8","line1":"8 Book Lane","line2":null,"city":"Madrid","region":null,"postalCode":"28008","country":"ES"}'::jsonb,
        29.99, 'EUR', '2026-10-14T09:50:08Z', NULL, '2026-10-14T09:51:28Z', 'PAYMENT_RETRY_EXHAUSTED'); -- C-08
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-009', 'CANCELLED', 'CUST-009', 'customer009@example.com', 'PM-009',
        '[{"sku":"SKU-002","quantity":1,"unitPrice":29.99}]'::jsonb,
        '{"name":"Arcadia Customer 9","line1":"9 Book Lane","line2":null,"city":"Madrid","region":null,"postalCode":"28009","country":"ES"}'::jsonb,
        29.99, 'EUR', '2026-10-14T09:50:09Z', '2026-10-14T09:50:39Z', '2026-10-14T09:51:39Z', 'PAYMENT_VOIDED'); -- C-09
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-010', 'CANCELLED', 'CUST-010', 'customer010@example.com', 'PM-010',
        '[{"sku":"SKU-002","quantity":1,"unitPrice":29.99}]'::jsonb,
        '{"name":"Arcadia Customer 10","line1":"10 Book Lane","line2":null,"city":"Madrid","region":null,"postalCode":"28010","country":"ES"}'::jsonb,
        29.99, 'EUR', '2026-10-14T09:50:10Z', '2026-10-14T09:50:40Z', '2026-10-14T09:51:40Z', 'PAYMENT_VOID_FAILED'); -- C-10
