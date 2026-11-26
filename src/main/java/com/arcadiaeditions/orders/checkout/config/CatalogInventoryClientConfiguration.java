package com.arcadiaeditions.orders.checkout.config;

import com.arcadiaeditions.orders.checkout.clients.inventory.InventoryApi;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.InventoryClient;
import com.arcadiaeditions.orders.checkout.infrastructure.inventory.InventoryClientRestAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Synchronous client to Catalog Inventory: the generated {@link InventoryApi} over a {@link RestClient}, on
 * arcadia.clients.catalog-inventory.* (application.yml).
 */
@Configuration
public class CatalogInventoryClientConfiguration {

    @Bean
    public InventoryApi catalogInventoryApi(
            @Value("${arcadia.clients.catalog-inventory.base-url}") String baseUrl,
            @Value("${arcadia.clients.catalog-inventory.username:}") String username,
            @Value("${arcadia.clients.catalog-inventory.password:}") String password) {
        var builder = RestClient.builder().baseUrl(baseUrl);
        if (!username.isEmpty()) {
            builder.requestInterceptor(new BasicAuthenticationInterceptor(username, password));
        }
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
                .build()
                .createClient(InventoryApi.class);
    }

    @Bean
    public InventoryClient catalogInventoryClient(InventoryApi catalogInventoryApi) {
        return new InventoryClientRestAdapter(catalogInventoryApi);
    }
}
