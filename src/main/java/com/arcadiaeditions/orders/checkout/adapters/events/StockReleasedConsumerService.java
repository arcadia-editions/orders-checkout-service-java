package com.arcadiaeditions.orders.checkout.adapters.events;

import com.arcadiaeditions.catalog.inventory.events.avro.StockReleased;
import com.arcadiaeditions.orders.checkout.asyncapi.client.IStockReleasedEventV1ConsumerService;
import com.arcadiaeditions.orders.checkout.core.inbound.OrdersCheckoutService;
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.CancelOrderInput;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: stock-released-event-v1 -> OrdersCheckoutService.cancelOrder.
 */
@Component
public class StockReleasedConsumerService implements IStockReleasedEventV1ConsumerService {

    private final OrdersCheckoutService ordersCheckoutService;

    public StockReleasedConsumerService(OrdersCheckoutService ordersCheckoutService) {
        this.ordersCheckoutService = ordersCheckoutService;
    }

    @Override
    public void onStockReleasedEventV1(StockReleased payload, StockReleasedHeaders headers) {
        ordersCheckoutService.cancelOrder(new CancelOrderInput()
                .setOrderId(payload.getOrderId().toString())
                .setReason(payload.getReason().name()));
    }
}
