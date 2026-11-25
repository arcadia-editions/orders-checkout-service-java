package com.arcadiaeditions.orders.checkout.core.outbound.inventory;

/** The two responses of reserveStock (ReserveStockOutcome RESERVED / UNAVAILABLE). */
public sealed interface ReserveStockResult permits StockReservedResult, StockUnavailableResult {

    String orderId();
}
