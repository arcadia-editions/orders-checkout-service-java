package com.arcadiaeditions.orders.checkout.adapters.events;

import com.arcadiaeditions.orders.checkout.asyncapi.client.IPaymentAuthorizedEventV1ConsumerService;
import com.arcadiaeditions.orders.checkout.core.inbound.OrdersCheckoutService;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: payment-authorized-event-v1 -> OrdersCheckoutService.confirmOrder.
 * Stub so the Spring context boots; the generator does not emit adapters for AsyncAPIGenerator interfaces.
 */
@Component
public class PaymentAuthorizedConsumerService implements IPaymentAuthorizedEventV1ConsumerService {

    private final OrdersCheckoutService ordersCheckoutService;

    public PaymentAuthorizedConsumerService(OrdersCheckoutService ordersCheckoutService) {
        this.ordersCheckoutService = ordersCheckoutService;
    }

    @Override
    public void onPaymentAuthorizedEvent(PaymentAuthorized payload, PaymentAuthorizedHeaders headers) {
        // TODO CUSTOM_REQUIRED: map payload to ConfirmOrderInput and call ordersCheckoutService.confirmOrder
        throw new UnsupportedOperationException("Not implemented");
    }
}
