package com.arcadiaeditions.orders.checkout.config;

import com.arcadiaeditions.orders.checkout.asyncapi.provider.InMemoryOrdersCheckoutEventsProducer;
import com.arcadiaeditions.orders.checkout.infrastructure.inventory.InventoryClientStub;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Replaces, on the {@code test} profile, what lives outside the service: Catalog Inventory (stub instead of the REST
 * adapter), the orderId generator (deterministic) and the events producer (captures instead of publishing to Kafka).
 */
@Configuration
@Profile("test")
public class TestCollaboratorsConfiguration {

    @Bean
    @Primary
    public InventoryClientStub inventoryClientStub(Clock clock) {
        return new InventoryClientStub(clock);
    }

    @Bean
    @Primary
    public OrderIdGeneratorStub orderIdGeneratorStub() {
        return new OrderIdGeneratorStub();
    }

    @Bean
    @Primary
    public InMemoryOrdersCheckoutEventsProducer capturingEventsProducer() {
        return new InMemoryOrdersCheckoutEventsProducer();
    }
}
