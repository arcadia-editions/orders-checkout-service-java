package com.arcadiaeditions.orders.checkout.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.schema.registry.client.ConfluentSchemaRegistryClient;
import org.springframework.cloud.stream.schema.registry.client.SchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Confluent Schema Registry (api-contract-commons asyncapi-master.yml: schemaRegistryVendor confluent) for the
 * {@code application/*+avro} bindings: this bean enables Spring Cloud Stream's AvroSchemaRegistryClientMessageConverter.
 */
@Configuration
public class SchemaRegistryConfiguration {

    @Bean
    public SchemaRegistryClient schemaRegistryClient(
            @Value("${spring.cloud.stream.schema-registry-client.endpoint}") String endpoint) {
        var client = new ConfluentSchemaRegistryClient();
        client.setEndpoint(endpoint);
        return client;
    }
}
