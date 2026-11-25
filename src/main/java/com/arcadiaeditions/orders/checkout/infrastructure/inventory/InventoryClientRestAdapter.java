package com.arcadiaeditions.orders.checkout.infrastructure.inventory;

import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ConfirmStockReservationResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.InventoryClient;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ReserveStockItem;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ReserveStockResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservationConfirmedResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservationNotHeldResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservedResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockUnavailableResult;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * REST adapter of {@link InventoryClient} on catalog-inventory-api/openapi.yml: POST /inventory/reservations/reserve
 * and POST /inventory/reservations/confirm. The {@link RestClient} carries the base URL and credentials
 * (CatalogInventoryClientConfiguration). HTTP errors propagate as RestClient exceptions (technical failures).
 */
public class InventoryClientRestAdapter implements InventoryClient {

    private final RestClient restClient;

    public InventoryClientRestAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ReserveStockResult reserveStock(String orderId, List<ReserveStockItem> items) {
        var request = new ReserveStockInputJson(
                orderId,
                items.stream()
                        .map(item -> new ReserveStockItemInputJson(item.sku(), item.quantity()))
                        .toList());
        var response = restClient
                .post()
                .uri("/inventory/reservations/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ReserveStockResultJson.class);
        if (response == null || response.outcome() == null) {
            throw new IllegalStateException("reserveStock: no outcome from catalog-inventory for " + orderId);
        }
        return switch (response.outcome()) {
            case "RESERVED" -> toReserved(orderId, response.reserved());
            case "UNAVAILABLE" -> toUnavailable(orderId, response.unavailable());
            default ->
                throw new IllegalStateException(
                        "reserveStock: unknown outcome " + response.outcome() + " for " + orderId);
        };
    }

    @Override
    public ConfirmStockReservationResult confirmStockReservation(String orderId) {
        var response = restClient
                .post()
                .uri("/inventory/reservations/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(new ConfirmStockReservationInputJson(orderId))
                .retrieve()
                .body(ConfirmStockReservationResultJson.class);
        if (response == null || response.outcome() == null) {
            throw new IllegalStateException(
                    "confirmStockReservation: no outcome from catalog-inventory for " + orderId);
        }
        return switch (response.outcome()) {
            case "CONFIRMED" -> {
                var confirmed = response.confirmed();
                if (confirmed == null) {
                    throw new IllegalStateException(
                            "confirmStockReservation: outcome CONFIRMED without confirmed body for " + orderId);
                }
                yield new StockReservationConfirmedResult(
                        orderIdOr(confirmed.orderId(), orderId), confirmed.confirmedAt());
            }
            case "NOT_HELD" -> {
                var notHeld = response.notHeld();
                yield new StockReservationNotHeldResult(
                        notHeld == null ? orderId : orderIdOr(notHeld.orderId(), orderId));
            }
            default ->
                throw new IllegalStateException(
                        "confirmStockReservation: unknown outcome " + response.outcome() + " for " + orderId);
        };
    }

    private static StockReservedResult toReserved(String orderId, StockReservedResultJson reserved) {
        if (reserved == null) {
            throw new IllegalStateException("reserveStock: outcome RESERVED without reserved body for " + orderId);
        }
        var lines = reserved.reservations() == null
                ? List.<StockReservedResult.StockReservedLine>of()
                : reserved.reservations().stream()
                        .map(line -> new StockReservedResult.StockReservedLine(
                                line.reservationId(), line.sku(), line.quantity(), line.reservedAt()))
                        .toList();
        return new StockReservedResult(orderIdOr(reserved.orderId(), orderId), reserved.expiresAt(), lines);
    }

    private static StockUnavailableResult toUnavailable(String orderId, StockUnavailableResultJson unavailable) {
        if (unavailable == null) {
            throw new IllegalStateException(
                    "reserveStock: outcome UNAVAILABLE without unavailable body for " + orderId);
        }
        var lines = unavailable.unavailableItems() == null
                ? List.<StockUnavailableResult.StockUnavailableLine>of()
                : unavailable.unavailableItems().stream()
                        .map(line -> new StockUnavailableResult.StockUnavailableLine(
                                line.sku(), line.requestedQuantity(), line.availableQuantity()))
                        .toList();
        return new StockUnavailableResult(orderIdOr(unavailable.orderId(), orderId), lines);
    }

    private static String orderIdOr(String fromResponse, String requested) {
        return fromResponse != null ? fromResponse : requested;
    }

    // wire shapes of catalog-inventory-api/openapi.yml components.schemas

    record ReserveStockInputJson(String orderId, List<ReserveStockItemInputJson> items) {}

    record ReserveStockItemInputJson(String sku, Integer quantity) {}

    record ConfirmStockReservationInputJson(String orderId) {}

    record ReserveStockResultJson(
            String outcome, StockReservedResultJson reserved, StockUnavailableResultJson unavailable) {}

    record StockReservedResultJson(String orderId, Instant expiresAt, List<StockReservedLineJson> reservations) {}

    record StockReservedLineJson(String reservationId, String sku, Integer quantity, Instant reservedAt) {}

    record StockUnavailableResultJson(String orderId, List<StockUnavailableLineJson> unavailableItems) {}

    record StockUnavailableLineJson(String sku, Integer requestedQuantity, Integer availableQuantity) {}

    record ConfirmStockReservationResultJson(
            String outcome, StockReservationConfirmedResultJson confirmed, StockReservationNotHeldResultJson notHeld) {}

    record StockReservationConfirmedResultJson(String orderId, Instant confirmedAt) {}

    record StockReservationNotHeldResultJson(String orderId) {}
}
