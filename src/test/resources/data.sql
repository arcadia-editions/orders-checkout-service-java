-- Basic test data: one consistent example per entity. Regenerate with $zenwave-test-data.
-- DDL exported 2026-11-25 to target/ddl/schema.sql (models: orders-checkout-api main).
-- JSON mirrors: src/test/resources/data/jpa/<Entity>/<k>.json, k = row position, id = 1 + (k-1)*50.
--
-- chain | business ids | state per aggregate | used by
-- C-01 | ORD-001, CUST-001, PM-001, SKU-001x1 | Order CONFIRMED (stock reserved+confirmed, payment authorized upstream) | generated repository tests (findById(1)), in-memory service tests

-- Orders Checkout
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-001', 'CONFIRMED', 'CUST-001', 'customer@example.com', 'PM-001',
        '[{"sku": "SKU-001", "quantity": 1, "unitPrice": 49.99}]'::jsonb,
        '{"name": "Arcadia Customer", "line1": "1 Book Lane", "line2": null, "city": "Madrid", "region": null, "postalCode": "28001", "country": "ES"}'::jsonb,
        49.99, 'EUR', '2026-09-26T13:00:00Z', '2026-09-26T13:05:00Z', NULL, NULL);
