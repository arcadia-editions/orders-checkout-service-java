package com.arcadiaeditions.orders.checkout.config;

import com.arcadiaeditions.orders.checkout.asyncapi.provider.*;
import com.arcadiaeditions.orders.checkout.core.application.*;
import com.arcadiaeditions.orders.checkout.core.domain.*;
import com.arcadiaeditions.orders.checkout.core.inbound.*;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Services InMemory Config. It can be used standalone or with @SpringBootTest.
 */
@Configuration
@Profile("in-memory")
public class ServicesInMemoryConfig extends RepositoriesInMemoryConfig {

    protected final EventsProducerInMemoryContext eventsProducerInMemoryContext = new EventsProducerInMemoryContext();

    protected final OrdersCheckoutServiceImpl ordersCheckoutService = new OrdersCheckoutServiceImpl(
            orderRepository(), eventsProducerInMemoryContext.ordersCheckoutEventsProducer());

    @Bean
    public <T extends OrdersCheckoutService> T ordersCheckoutService() {
        return (T) ordersCheckoutService;
    }

    static List<Order> _orders;

    public void reloadTestData() {

        var testDataLoader = new TestDataLoader(List.of(Order.class, OrderItem.class, ShippingAddress.class));
        var orders = _orders != null ? _orders : testDataLoader.loadCollectionTestDataAsObjects(Order.class);
        orderRepository().deleteAll();
        orderRepository().saveAll(orders);
    }

    public EventsProducerInMemoryContext getEventsProducerInMemoryContext() {
        return eventsProducerInMemoryContext;
    }
}
