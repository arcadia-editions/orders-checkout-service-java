package com.arcadiaeditions.orders.checkout.core.outbound.inventory;

import java.util.List;

/**
 * Outbound port to Catalog Inventory (InventoryService of catalog-inventory-api/domain-model.zdl), called
 * synchronously by startOrderCheckout (reserveStock) and confirmOrder (confirmStockReservation).
 * Technical failures (unreachable service, unexpected HTTP status) are thrown, never returned as a result.
 */
public interface InventoryClient {

    /** {@link StockReservedResult} or {@link StockUnavailableResult} (place-order-flow.zfl reserveStock). */
    ReserveStockResult reserveStock(String orderId, List<ReserveStockItem> items);

    /**
     * {@link StockReservationConfirmedResult} or {@link StockReservationNotHeldResult} (place-order-flow.zfl
     * confirmStockReservation).
     */
    ConfirmStockReservationResult confirmStockReservation(String orderId);
}
