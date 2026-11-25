package com.arcadiaeditions.orders.checkout.core.outbound.inventory;

/** One line of ReserveStockInput.items. */
public record ReserveStockItem(String sku, Integer quantity) {}
