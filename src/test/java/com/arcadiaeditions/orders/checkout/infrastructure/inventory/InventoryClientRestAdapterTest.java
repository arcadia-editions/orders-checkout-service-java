package com.arcadiaeditions.orders.checkout.infrastructure.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ReserveStockItem;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservationConfirmedResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservationNotHeldResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservedResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockUnavailableResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Request / response mapping of the REST adapter against catalog-inventory-api/openapi.yml. */
class InventoryClientRestAdapterTest {

    static final String BASE_URL = "http://catalog-inventory.test/api";

    MockRestServiceServer server;

    InventoryClientRestAdapter adapter;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new InventoryClientRestAdapter(builder.build());
    }

    @Test
    void reserveStockReserved() {
        server.expect(requestTo(BASE_URL + "/inventory/reservations/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"orderId":"ORD-100","items":[{"sku":"SKU-002","quantity":2}]}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                        {"outcome":"RESERVED","reserved":{"orderId":"ORD-100","expiresAt":"2026-10-14T10:10:00Z",
                         "reservations":[{"reservationId":"RSV-1","sku":"SKU-002","quantity":2,"reservedAt":"2026-10-14T10:00:00Z"}]}}
                        """));

        var result = adapter.reserveStock("ORD-100", List.of(new ReserveStockItem("SKU-002", 2)));

        var reserved = assertInstanceOf(StockReservedResult.class, result);
        assertEquals("ORD-100", reserved.orderId());
        assertEquals(Instant.parse("2026-10-14T10:10:00Z"), reserved.expiresAt());
        assertEquals(
                new StockReservedResult.StockReservedLine("RSV-1", "SKU-002", 2, Instant.parse("2026-10-14T10:00:00Z")),
                reserved.reservations().get(0));
        server.verify();
    }

    @Test
    void reserveStockUnavailable() {
        server.expect(requestTo(BASE_URL + "/inventory/reservations/reserve"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                        {"outcome":"UNAVAILABLE","unavailable":{"orderId":"ORD-101",
                         "unavailableItems":[{"sku":"SKU-004","requestedQuantity":1,"availableQuantity":0}]}}
                        """));

        var result = adapter.reserveStock("ORD-101", List.of(new ReserveStockItem("SKU-004", 1)));

        var unavailable = assertInstanceOf(StockUnavailableResult.class, result);
        assertEquals("ORD-101", unavailable.orderId());
        assertEquals(
                List.of(new StockUnavailableResult.StockUnavailableLine("SKU-004", 1, 0)),
                unavailable.unavailableItems());
        server.verify();
    }

    @Test
    void confirmStockReservationConfirmed() {
        server.expect(requestTo(BASE_URL + "/inventory/reservations/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"orderId":"ORD-002"}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                        {"outcome":"CONFIRMED","confirmed":{"orderId":"ORD-002","confirmedAt":"2026-10-14T10:00:00Z"}}
                        """));

        var result = adapter.confirmStockReservation("ORD-002");

        assertEquals(
                new StockReservationConfirmedResult("ORD-002", Instant.parse("2026-10-14T10:00:00Z")),
                assertInstanceOf(StockReservationConfirmedResult.class, result));
        server.verify();
    }

    @Test
    void confirmStockReservationNotHeld() {
        server.expect(requestTo(BASE_URL + "/inventory/reservations/confirm"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                        {"outcome":"NOT_HELD","notHeld":{"orderId":"ORD-005"}}
                        """));

        var result = adapter.confirmStockReservation("ORD-005");

        assertEquals(
                new StockReservationNotHeldResult("ORD-005"),
                assertInstanceOf(StockReservationNotHeldResult.class, result));
        server.verify();
    }

    @Test
    void httpErrorIsATechnicalFailure() {
        server.expect(requestTo(BASE_URL + "/inventory/reservations/confirm")).andRespond(withBadRequest());

        assertThrows(RestClientResponseException.class, () -> adapter.confirmStockReservation("ORD-002"));
    }
}
