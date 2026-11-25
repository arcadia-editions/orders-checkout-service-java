package com.arcadiaeditions.orders.checkout.core.outbound.inventory;

import java.util.List;

/** Some line cannot be reserved; nothing is reserved. */
public record StockUnavailableResult(String orderId, List<StockUnavailableLine> unavailableItems)
        implements ReserveStockResult {

    public StockUnavailableResult {
        unavailableItems = unavailableItems == null ? List.of() : List.copyOf(unavailableItems);
    }

    public record StockUnavailableLine(String sku, Integer requestedQuantity, Integer availableQuantity) {}
}
