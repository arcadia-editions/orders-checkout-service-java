package com.arcadiaeditions.orders.checkout.core.outbound.inventory;

/** The two responses of confirmStockReservation (ConfirmStockReservationOutcome CONFIRMED / NOT_HELD, P-01 A). */
public sealed interface ConfirmStockReservationResult
        permits StockReservationConfirmedResult, StockReservationNotHeldResult {

    String orderId();
}
