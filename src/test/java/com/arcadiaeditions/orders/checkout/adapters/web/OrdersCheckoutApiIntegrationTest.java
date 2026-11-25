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
     * Test: getOrder for OK.
     */
    @Test
    public void testGetOrder_200() {
        var id = 1L; // data.sql C-01 / data/jpa/Order/1.json: ORD-001 CONFIRMED

        webTestClient
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
                .isEqualTo("ORD-001")
                .jsonPath("$.status")
                .isEqualTo("CONFIRMED")
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
                .isNotEmpty()
                .jsonPath("$.confirmedAt")
                .isNotEmpty();
    }

    /**
     * Test: getOrder for Invalid request.
     */
    @Test
    public void testGetOrder_400() {
        var id = "";

        webTestClient
                .method(GET)
                .uri("/api/orders/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(400);
    }
}
