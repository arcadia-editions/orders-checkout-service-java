package com.arcadiaeditions.orders.checkout.adapters.web;

import static org.springframework.http.HttpMethod.*;

import com.arcadiaeditions.orders.checkout.adapters.web.dtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Integration tests for the {@link OrdersCheckoutApi} REST controller.
 */
public class OrdersCheckoutApiIntegrationTest extends BaseWebTestClientTest {

    /**
     * Test: Persists the Order only on OrderCreated; StockUnavailable answers 409 and persists nothing (P-03 A). for OK.
     */
    @Test
    public void testStartOrderCheckout_201() {
        String requestBody = """
            {
              "customerId" : "CUS-100",
              "customerEmail" : "reader@example.com",
              "paymentMethodId" : "PM-100",
              "currency" : "EUR",
              "items" : [ {
                "sku" : "SKU-001",
                "quantity" : 2,
                "unitPrice" : 10.50
              } ],
              "shippingAddress" : {
                "name" : "Reader One",
                "line1" : "1 Main St",
                "city" : "Madrid",
                "postalCode" : "28001",
                "country" : "ES"
              }
            }
        """;

        webTestClient
                .method(POST)
                .uri("/api/orders/start-checkout")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isEqualTo(201)
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isNotEmpty()
                .jsonPath("$.version")
                .isNotEmpty()
                .jsonPath("$.orderId")
                .isNotEmpty()
                .jsonPath("$.status")
                .isEqualTo("CREATED")
                .jsonPath("$.customerId")
                .isNotEmpty()
                .jsonPath("$.customerEmail")
                .isNotEmpty()
                .jsonPath("$.paymentMethodId")
                .isNotEmpty()
                .jsonPath("$.items")
                .isNotEmpty()
                .jsonPath("$.items")
                .isArray()
                .jsonPath("$.items[0].sku")
                .isNotEmpty()
                .jsonPath("$.items[0].quantity")
                .isNotEmpty()
                .jsonPath("$.items[0].unitPrice")
                .isNotEmpty()
                .jsonPath("$.shippingAddress")
                .isNotEmpty()
                .jsonPath("$.shippingAddress.name")
                .isNotEmpty()
                .jsonPath("$.shippingAddress.line1")
                .isNotEmpty()
                .jsonPath("$.shippingAddress.city")
                .isNotEmpty()
                .jsonPath("$.shippingAddress.postalCode")
                .isNotEmpty()
                .jsonPath("$.shippingAddress.country")
                .isNotEmpty()
                .jsonPath("$.totalAmount")
                .isEqualTo(21.0)
                .jsonPath("$.currency")
                .isEqualTo("EUR")
                .jsonPath("$.createdAt")
                .isNotEmpty();
    }

    /**
     * Test: Persists the Order only on OrderCreated; StockUnavailable answers 409 and persists nothing (P-03 A). for Invalid request.
     */
    @Test
    public void testStartOrderCheckout_400() {
        String requestBody = """
            {
              "customerId" : "customerId-5akhslrgnu",
              "customerEmail" : "dane.bruen@yahoo.com",
              "paymentMethodId" : "paymentMethodId-z640geirge",
              "currency" : "currency-wu8",
              "items" : [ {
                "sku" : "sku-a3jgfs",
                "quantity" : 90,
                "unitPrice" : 98
              } ],
              "shippingAddress" : {
                "name" : "name-t",
                "line1" : "line1-4863lgtxlj",
                "line2" : "line2-bi0o",
                "city" : "Gleasonton",
                "region" : "region-h",
                "postalCode" : "postalCode-jc",
                "country" : "Lithuania"
              }
            }
        """;

        webTestClient
                .method(POST)
                .uri("/api/orders/start-checkout")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isEqualTo(400);
    }

    /**
     * Test: Persists the Order only on OrderCreated; StockUnavailable answers 409 and persists nothing (P-03 A). for Stock unavailable; no Order persisted.
     */
    @Test
    public void testStartOrderCheckout_409() {
        String requestBody = """
            {
              "customerId" : "CUS-100",
              "customerEmail" : "reader@example.com",
              "paymentMethodId" : "PM-100",
              "currency" : "EUR",
              "items" : [ {
                "sku" : "SKU-004",
                "quantity" : 8,
                "unitPrice" : 13
              } ],
              "shippingAddress" : {
                "name" : "Reader One",
                "line1" : "1 Main St",
                "city" : "Madrid",
                "postalCode" : "28001",
                "country" : "ES"
              }
            }
        """;

        webTestClient
                .method(POST)
                .uri("/api/orders/start-checkout")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.orderId")
                .isNotEmpty()
                .jsonPath("$.unavailableItems")
                .isNotEmpty()
                .jsonPath("$.unavailableItems")
                .isArray()
                .jsonPath("$.unavailableItems[0].sku")
                .isNotEmpty()
                .jsonPath("$.unavailableItems[0].requestedQuantity")
                .isNotEmpty()
                .jsonPath("$.unavailableItems[0].availableQuantity")
                .isNotEmpty();
    }

    /**
     * getOrder 200: CONFIRMED (C-01), CREATED (C-02), CANCELLED (C-06).
     * Numeric ids from data.sql (sequence step 50): 1, 51, 251.
     */
    @Test
    public void testGetOrder_200() {
        assertGetOrder(1L, "ORD-001", "CONFIRMED", null);
        assertGetOrder(51L, "ORD-002", "CREATED", null);
        assertGetOrder(251L, "ORD-006", "CANCELLED", "RESERVATION_EXPIRED");
    }

    private void assertGetOrder(long id, String orderId, String status, String cancellationReason) {
        var body = webTestClient
                .method(GET)
                .uri("/api/orders/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(200)
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isNotEmpty()
                .jsonPath("$.version")
                .isNotEmpty()
                .jsonPath("$.orderId")
                .isEqualTo(orderId)
                .jsonPath("$.status")
                .isEqualTo(status)
                .jsonPath("$.customerId")
                .isNotEmpty()
                .jsonPath("$.customerEmail")
                .isNotEmpty()
                .jsonPath("$.paymentMethodId")
                .isNotEmpty()
                .jsonPath("$.items")
                .isNotEmpty()
                .jsonPath("$.items")
                .isArray()
                .jsonPath("$.items[0].sku")
                .isNotEmpty()
                .jsonPath("$.items[0].quantity")
                .isNotEmpty()
                .jsonPath("$.items[0].unitPrice")
                .isNotEmpty()
                .jsonPath("$.shippingAddress")
                .isNotEmpty()
                .jsonPath("$.shippingAddress.name")
                .isNotEmpty()
                .jsonPath("$.shippingAddress.line1")
                .isNotEmpty()
                .jsonPath("$.shippingAddress.city")
                .isNotEmpty()
                .jsonPath("$.shippingAddress.postalCode")
                .isNotEmpty()
                .jsonPath("$.shippingAddress.country")
                .isNotEmpty()
                .jsonPath("$.totalAmount")
                .isNotEmpty()
                .jsonPath("$.currency")
                .isNotEmpty()
                .jsonPath("$.createdAt")
                .isNotEmpty();
        if ("CONFIRMED".equals(status)) {
            body.jsonPath("$.confirmedAt").isNotEmpty();
        }
        if (cancellationReason != null) {
            body.jsonPath("$.cancellationReason").isEqualTo(cancellationReason);
        }
    }

    /**
     * openapi.yml GET /orders/{id} responds 400 Invalid request. id is int64, so a non-numeric id is 400.
     */
    @Test
    public void testGetOrder_400() {
        webTestClient
                .method(GET)
                .uri("/api/orders/{id}", "not-a-number")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(400);
    }

    /**
     * Unknown numeric id. data.sql: not found -> any id not listed (e.g. 999).
     * openapi.yml does not declare 404; the controller already answers notFound().
     */
    @Test
    public void testGetOrder_404() {
        var id = 999L;

        webTestClient
                .method(GET)
                .uri("/api/orders/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(404);
    }
}
