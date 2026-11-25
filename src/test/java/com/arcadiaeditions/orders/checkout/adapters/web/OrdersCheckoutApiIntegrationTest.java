package com.arcadiaeditions.orders.checkout.adapters.web;

import com.arcadiaeditions.orders.checkout.adapters.web.*;
import com.arcadiaeditions.orders.checkout.adapters.web.dtos.*;
import com.arcadiaeditions.orders.checkout.adapters.web.BaseWebTestClientTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;

import static org.springframework.http.HttpMethod.*;

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
              "customerId" : "customerId-6i3m79xjb5uk5c",
              "customerEmail" : "lindsay.kuhic@gmail.com",
              "paymentMethodId" : "paymentMethodId-s3c",
              "currency" : "currency-p47w3wk72mejl7yof",
              "items" : [ {
                "sku" : "sku-8t3cv0aw69q81xsr0otp5f",
                "quantity" : 83,
                "unitPrice" : 99
              } ],
              "shippingAddress" : {
                "name" : "name-tnr7ehins",
                "line1" : "line1-wim4ehii1ay202qc4ki0",
                "line2" : "line2-ugbmqqk0yqur",
                "city" : "Curtmouth",
                "region" : "region-j3n3t",
                "postalCode" : "postalCode-0ua5o9cw6aty376",
                "country" : "Falkland Islands (Malvinas"
              }
            }
        """;

        webTestClient.method(POST).uri("/api/orders/start-checkout")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isEqualTo(201)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isNotEmpty()
            .jsonPath("$.version").isNotEmpty()
            .jsonPath("$.orderId").isNotEmpty()
            .jsonPath("$.status").isNotEmpty()
            .jsonPath("$.customerId").isNotEmpty()
            .jsonPath("$.customerEmail").isNotEmpty()
            .jsonPath("$.paymentMethodId").isNotEmpty()
            .jsonPath("$.items").isNotEmpty()
            .jsonPath("$.items").isArray()
            .jsonPath("$.items[0].sku").isNotEmpty()
            .jsonPath("$.items[0].quantity").isNotEmpty()
            .jsonPath("$.items[0].unitPrice").isNotEmpty()
            .jsonPath("$.shippingAddress").isNotEmpty()
            .jsonPath("$.shippingAddress.name").isNotEmpty()
            .jsonPath("$.shippingAddress.line1").isNotEmpty()
            .jsonPath("$.shippingAddress.line2").isNotEmpty()
            .jsonPath("$.shippingAddress.city").isNotEmpty()
            .jsonPath("$.shippingAddress.region").isNotEmpty()
            .jsonPath("$.shippingAddress.postalCode").isNotEmpty()
            .jsonPath("$.shippingAddress.country").isNotEmpty()
            .jsonPath("$.totalAmount").isNotEmpty()
            .jsonPath("$.currency").isNotEmpty()
            .jsonPath("$.createdAt").isNotEmpty()
            .jsonPath("$.confirmedAt").isNotEmpty()
            .jsonPath("$.cancelledAt").isNotEmpty()
            .jsonPath("$.cancellationReason").isNotEmpty();
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

        webTestClient.method(POST).uri("/api/orders/start-checkout")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isEqualTo(400);
    }

    /**
    * Test: Persists the Order only on OrderCreated; StockUnavailable answers 409 and persists nothing (P-03 A). for Stock unavailable; no Order persisted.
    */
    @Test
    public void testStartOrderCheckout_409() {
        String requestBody = """
            {
              "customerId" : "customerId-tkflgygvnbo7bka",
              "customerEmail" : "jospeh.ebert@hotmail.com",
              "paymentMethodId" : "paymentMethodId-3dou9278ex",
              "currency" : "currency-cb3ts3zef4uz0ru",
              "items" : [ {
                "sku" : "sku-qelk",
                "quantity" : 8,
                "unitPrice" : 13
              } ],
              "shippingAddress" : {
                "name" : "name-0uy7j5ljouxcemr7rgc",
                "line1" : "line1-czivz9h4",
                "line2" : "line2-x",
                "city" : "South Craig",
                "region" : "region-jfgr0z1",
                "postalCode" : "postalCode-ba0z3a9v3",
                "country" : "Honduras"
              }
            }
        """;

        webTestClient.method(POST).uri("/api/orders/start-checkout")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.orderId").isNotEmpty()
            .jsonPath("$.unavailableItems").isNotEmpty()
            .jsonPath("$.unavailableItems").isArray()
            .jsonPath("$.unavailableItems[0].sku").isNotEmpty()
            .jsonPath("$.unavailableItems[0].requestedQuantity").isNotEmpty()
            .jsonPath("$.unavailableItems[0].availableQuantity").isNotEmpty();
    }

    /**
    * Test: getOrder for OK.
    */
    @Test
    public void testGetOrder_200() {
        var id = "";

        webTestClient.method(GET).uri("/api/orders/{id}", id)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isEqualTo(200)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isNotEmpty()
            .jsonPath("$.version").isNotEmpty()
            .jsonPath("$.orderId").isNotEmpty()
            .jsonPath("$.status").isNotEmpty()
            .jsonPath("$.customerId").isNotEmpty()
            .jsonPath("$.customerEmail").isNotEmpty()
            .jsonPath("$.paymentMethodId").isNotEmpty()
            .jsonPath("$.items").isNotEmpty()
            .jsonPath("$.items").isArray()
            .jsonPath("$.items[0].sku").isNotEmpty()
            .jsonPath("$.items[0].quantity").isNotEmpty()
            .jsonPath("$.items[0].unitPrice").isNotEmpty()
            .jsonPath("$.shippingAddress").isNotEmpty()
            .jsonPath("$.shippingAddress.name").isNotEmpty()
            .jsonPath("$.shippingAddress.line1").isNotEmpty()
            .jsonPath("$.shippingAddress.line2").isNotEmpty()
            .jsonPath("$.shippingAddress.city").isNotEmpty()
            .jsonPath("$.shippingAddress.region").isNotEmpty()
            .jsonPath("$.shippingAddress.postalCode").isNotEmpty()
            .jsonPath("$.shippingAddress.country").isNotEmpty()
            .jsonPath("$.totalAmount").isNotEmpty()
            .jsonPath("$.currency").isNotEmpty()
            .jsonPath("$.createdAt").isNotEmpty()
            .jsonPath("$.confirmedAt").isNotEmpty()
            .jsonPath("$.cancelledAt").isNotEmpty()
            .jsonPath("$.cancellationReason").isNotEmpty();
    }

    /**
    * Test: getOrder for Invalid request.
    */
    @Test
    public void testGetOrder_400() {
        var id = "";

        webTestClient.method(GET).uri("/api/orders/{id}", id)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isEqualTo(400);
    }

}
