package com.arcadiaeditions.orders.checkout.infrastructure.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.arcadiaeditions.orders.checkout.config.TestClock;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.InventoryClient;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ReserveStockItem;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservationConfirmedResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservationNotHeldResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservedResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockUnavailableResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** In-memory stub of the catalog-inventory port: SKU-004 unavailable, ORD-005 not held (data.sql C-05). */
class InventoryClientStubTest {

    InventoryClient inventoryClient = new InventoryClientStub(TestClock.FIXED);

    @Test
    void reserveStockReservesAvailableSkus() {
        var result = inventoryClient.reserveStock(
                "ORD-100", List.of(new ReserveStockItem("SKU-002", 2), new ReserveStockItem("SKU-003", 1)));

        var reserved = assertInstanceOf(StockReservedResult.class, result);
        assertEquals("ORD-100", reserved.orderId());
        assertEquals(TestClock.NOW.plus(Duration.ofMinutes(10)), reserved.expiresAt());
        assertEquals(2, reserved.reservations().size());
        assertEquals("SKU-002", reserved.reservations().get(0).sku());
        assertEquals(2, reserved.reservations().get(0).quantity());
        assertEquals(TestClock.NOW, reserved.reservations().get(0).reservedAt());
    }

    @Test
    void reserveStockAnswersUnavailableForSku004() {
        var result = inventoryClient.reserveStock(
                "ORD-101", List.of(new ReserveStockItem("SKU-002", 1), new ReserveStockItem("SKU-004", 3)));

        var unavailable = assertInstanceOf(StockUnavailableResult.class, result);
        assertEquals("ORD-101", unavailable.orderId());
        assertEquals(1, unavailable.unavailableItems().size());
        var line = unavailable.unavailableItems().get(0);
        assertEquals("SKU-004", line.sku());
        assertEquals(3, line.requestedQuantity());
        assertEquals(0, line.availableQuantity());
    }

    @Test
    void confirmStockReservationConfirms() {
        var result = inventoryClient.confirmStockReservation("ORD-002");

        var confirmed = assertInstanceOf(StockReservationConfirmedResult.class, result);
        assertEquals("ORD-002", confirmed.orderId());
        assertEquals(TestClock.NOW, confirmed.confirmedAt());
    }

    @Test
    void confirmStockReservationAnswersNotHeldForOrd005() {
        var result = inventoryClient.confirmStockReservation("ORD-005");

        var notHeld = assertInstanceOf(StockReservationNotHeldResult.class, result);
        assertEquals("ORD-005", notHeld.orderId());
    }
}
