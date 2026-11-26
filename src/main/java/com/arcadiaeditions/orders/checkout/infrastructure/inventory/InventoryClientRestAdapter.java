package com.arcadiaeditions.orders.checkout.infrastructure.inventory;

import com.arcadiaeditions.orders.checkout.clients.inventory.InventoryApi;
import com.arcadiaeditions.orders.checkout.clients.inventory.dtos.ConfirmStockReservationInputDTO;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ConfirmStockReservationResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.InventoryClient;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ReserveStockItem;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ReserveStockResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservationNotHeldResult;
import java.util.List;

/**
 * REST adapter of {@link InventoryClient} on the generated {@link InventoryApi} (catalog-inventory-api/openapi.yml:
 * POST /inventory/reservations/reserve and POST /inventory/reservations/confirm). The client carries the base URL and
 * credentials (CatalogInventoryClientConfiguration). HTTP errors propagate as RestClient exceptions (technical
 * failures).
 */
public class InventoryClientRestAdapter implements InventoryClient {

    private final InventoryApi inventoryApi;

    private final InventoryClientMapper mapper = InventoryClientMapper.INSTANCE;

    public InventoryClientRestAdapter(InventoryApi inventoryApi) {
        this.inventoryApi = inventoryApi;
    }

    @Override
    public ReserveStockResult reserveStock(String orderId, List<ReserveStockItem> items) {
        var response = inventoryApi
                .reserveStock(mapper.asReserveStockInput(orderId, items))
                .getBody();
        if (response == null || response.getOutcome() == null) {
            throw new IllegalStateException("reserveStock: no outcome from catalog-inventory for " + orderId);
        }
        return switch (response.getOutcome()) {
            case RESERVED -> {
                if (response.getReserved() == null) {
                    throw new IllegalStateException(
                            "reserveStock: outcome RESERVED without reserved body for " + orderId);
                }
                yield mapper.asStockReservedResult(response.getReserved(), orderId);
            }
            case UNAVAILABLE -> {
                if (response.getUnavailable() == null) {
                    throw new IllegalStateException(
                            "reserveStock: outcome UNAVAILABLE without unavailable body for " + orderId);
                }
                yield mapper.asStockUnavailableResult(response.getUnavailable(), orderId);
            }
        };
    }

    @Override
    public ConfirmStockReservationResult confirmStockReservation(String orderId) {
        var response = inventoryApi
                .confirmStockReservation(new ConfirmStockReservationInputDTO(orderId))
                .getBody();
        if (response == null || response.getOutcome() == null) {
            throw new IllegalStateException(
                    "confirmStockReservation: no outcome from catalog-inventory for " + orderId);
        }
        return switch (response.getOutcome()) {
            case CONFIRMED -> {
                if (response.getConfirmed() == null) {
                    throw new IllegalStateException(
                            "confirmStockReservation: outcome CONFIRMED without confirmed body for " + orderId);
                }
                yield mapper.asStockReservationConfirmedResult(response.getConfirmed(), orderId);
            }
            case NOT_HELD -> {
                var notHeld = response.getNotHeld();
                yield new StockReservationNotHeldResult(
                        notHeld == null ? orderId : mapper.orderIdOr(notHeld.getOrderId(), orderId));
            }
        };
    }
}
