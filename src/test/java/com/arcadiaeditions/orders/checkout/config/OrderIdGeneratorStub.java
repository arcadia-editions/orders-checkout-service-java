package com.arcadiaeditions.orders.checkout.config;

import com.arcadiaeditions.orders.checkout.core.outbound.ids.OrderIdGenerator;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic orderIds for tests: ORD-100, ORD-101, ... (the fixtures use ORD-001..ORD-010). */
public class OrderIdGeneratorStub implements OrderIdGenerator {

    public static final int FIRST = 100;

    private final AtomicInteger next = new AtomicInteger(FIRST);

    @Override
    public String nextOrderId() {
        return "ORD-" + next.getAndIncrement();
    }
}
