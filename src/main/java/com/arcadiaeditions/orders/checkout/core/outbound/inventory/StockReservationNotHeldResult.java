package com.arcadiaeditions.orders.checkout.core.outbound.inventory;

/** Some line of the order is no longer RESERVED (released by the timer). Nothing is confirmed. */
public record StockReservationNotHeldResult(String orderId) implements ConfirmStockReservationResult {}
