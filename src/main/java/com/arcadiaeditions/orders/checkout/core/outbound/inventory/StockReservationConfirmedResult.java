package com.arcadiaeditions.orders.checkout.core.outbound.inventory;

import java.time.Instant;

/** Every reservation of the order is CONFIRMED (also on redelivery). */
public record StockReservationConfirmedResult(String orderId, Instant confirmedAt)
        implements ConfirmStockReservationResult {}
