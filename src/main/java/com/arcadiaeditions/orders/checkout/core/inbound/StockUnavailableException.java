package com.arcadiaeditions.orders.checkout.core.inbound;

import java.util.List;

/**
 * startOrderCheckout outcome StockUnavailable: nothing persisted, the web adapter answers 409 with the
 * StockUnavailable body.
 */
public class StockUnavailableException extends RuntimeException {

    private final String orderId;
    private final List<Line> unavailableItems;

    public StockUnavailableException(String orderId, List<Line> unavailableItems) {
        super("Stock unavailable for order " + orderId);
        this.orderId = orderId;
        this.unavailableItems = List.copyOf(unavailableItems);
    }

    public String getOrderId() {
        return orderId;
    }

    public List<Line> getUnavailableItems() {
        return unavailableItems;
    }

    /** An item that cannot be reserved. */
    public record Line(String sku, Integer requestedQuantity, Integer availableQuantity) {}
}
