package com.arcadiaeditions.orders.checkout.adapters.events;

import com.arcadiaeditions.catalog.inventory.events.avro.StockReleased;
import com.arcadiaeditions.orders.checkout.asyncapi.client.IStockReleasedEventV1ConsumerService;
import com.arcadiaeditions.orders.checkout.core.inbound.OrdersCheckoutService;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: stock-released-event-v1 -> OrdersCheckoutService.cancelOrder.
 * Stub so the Spring context boots; the generator does not emit adapters for AsyncAPIGenerator interfaces.
 */
@Component
public class StockReleasedConsumerService implements IStockReleasedEventV1ConsumerService {

    private final OrdersCheckoutService ordersCheckoutService;

    public StockReleasedConsumerService(OrdersCheckoutService ordersCheckoutService) {
        this.ordersCheckoutService = ordersCheckoutService;
    }

    @Override
    public void onStockReleasedEventV1(StockReleased payload, StockReleasedHeaders headers) {
        // TODO CUSTOM_REQUIRED: map payload to CancelOrderInput (orderId, reason) and call ordersCheckoutService.cancelOrder
        throw new UnsupportedOperationException("Not implemented");
    }
}
