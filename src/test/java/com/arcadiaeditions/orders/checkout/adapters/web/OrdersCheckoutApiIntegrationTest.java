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
    * Test: startOrderCheckout for OK.
    */
    @Test
    public void testStartOrderCheckout_201() {
        String requestBody = """
            {
              "customerId" : "customerId-6oy4goys4s5gooj",
              "customerEmail" : "brittaney.beahan@hotmail.c",
              "paymentMethodId" : "paymentMethodId-wikwc9jmdb",
              "currency" : "currency-x25gxiz9x0i6absm0",
              "items" : [ {
                "sku" : "sku-lx9039r3mrqcdcel7",
                "quantity" : 13,
                "unitPrice" : 5
              } ],
              "shippingAddress" : {
                "name" : "name-i6kym1vofm6",
                "line1" : "line1-2jl495k9ny0v3kdox8m7",
                "line2" : "line2-7ndz801i4r",
                "city" : "Hoegershire",
                "region" : "region-9pun4wvpqsaox8mr3cd",
                "postalCode" : "postalCode-fyub1vdt4hpa8k",
                "country" : "Guam"
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

}
