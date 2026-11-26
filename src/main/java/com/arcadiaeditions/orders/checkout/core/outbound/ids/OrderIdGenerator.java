package com.arcadiaeditions.orders.checkout.core.outbound.ids;

import java.util.function.Supplier;

/**
 * Generates the business key Order.orderId before reserveStock (ADR 0001): a UUIDv7 string in main, a deterministic
 * stub in tests.
 */
@FunctionalInterface
public interface OrderIdGenerator extends Supplier<String> {

    String nextOrderId();

    @Override
    default String get() {
        return nextOrderId();
    }
}
