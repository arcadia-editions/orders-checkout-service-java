package com.arcadiaeditions.orders.checkout.core.outbound.inventory;

import java.time.Instant;
import java.util.List;

/** Every line of the order is reserved until {@code expiresAt}. */
public record StockReservedResult(String orderId, Instant expiresAt, List<StockReservedLine> reservations)
        implements ReserveStockResult {

    public StockReservedResult {
        reservations = reservations == null ? List.of() : List.copyOf(reservations);
    }

    public record StockReservedLine(String reservationId, String sku, Integer quantity, Instant reservedAt) {}
}
