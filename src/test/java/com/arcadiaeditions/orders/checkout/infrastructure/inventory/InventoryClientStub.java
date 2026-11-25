package com.arcadiaeditions.orders.checkout.infrastructure.inventory;

import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ConfirmStockReservationResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.InventoryClient;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ReserveStockItem;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ReserveStockResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservationConfirmedResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservationNotHeldResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservedResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockUnavailableResult;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-memory stub of Catalog Inventory for tests (data.sql header): the SKUs in {@link #unavailableSkus()} (SKU-004,
 * zero stock) are unavailable, every other SKU is reserved for 10 minutes; confirm answers NOT_HELD for the orderIds
 * in {@link #notHeldOrderIds()} (ORD-005, chain C-05) and CONFIRMED otherwise.
 */
public class InventoryClientStub implements InventoryClient {

    public static final Duration RESERVATION_TTL = Duration.ofMinutes(10);

    private final Clock clock;

    private final Set<String> unavailableSkus = new HashSet<>(Set.of("SKU-004"));

    private final Set<String> notHeldOrderIds = new HashSet<>(Set.of("ORD-005"));

    public InventoryClientStub(Clock clock) {
        this.clock = clock;
    }

    /** Mutable: tests may add SKUs to answer UNAVAILABLE. */
    public Set<String> unavailableSkus() {
        return unavailableSkus;
    }

    /** Mutable: tests may add orderIds to answer NOT_HELD. */
    public Set<String> notHeldOrderIds() {
        return notHeldOrderIds;
    }

    @Override
    public ReserveStockResult reserveStock(String orderId, List<ReserveStockItem> items) {
        var unavailable = items.stream()
                .filter(item -> unavailableSkus.contains(item.sku()))
                .map(item -> new StockUnavailableResult.StockUnavailableLine(item.sku(), item.quantity(), 0))
                .toList();
        if (!unavailable.isEmpty()) {
            return new StockUnavailableResult(orderId, unavailable);
        }
        var now = clock.instant();
        var lines = new ArrayList<StockReservedResult.StockReservedLine>();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            lines.add(new StockReservedResult.StockReservedLine(
                    "RSV-" + orderId + "-" + (i + 1), item.sku(), item.quantity(), now));
        }
        return new StockReservedResult(orderId, now.plus(RESERVATION_TTL), lines);
    }

    @Override
    public ConfirmStockReservationResult confirmStockReservation(String orderId) {
        if (notHeldOrderIds.contains(orderId)) {
            return new StockReservationNotHeldResult(orderId);
        }
        return new StockReservationConfirmedResult(orderId, clock.instant());
    }
}
