package com.arcadiaeditions.orders.checkout.adapters.events;

import com.arcadiaeditions.orders.checkout.asyncapi.client.IPaymentAuthorizedEventV1ConsumerService;
import com.arcadiaeditions.orders.checkout.core.inbound.OrdersCheckoutService;
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.ConfirmOrderInput;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: payment-authorized-event-v1 -> OrdersCheckoutService.confirmOrder.
 */
@Component
public class PaymentAuthorizedConsumerService implements IPaymentAuthorizedEventV1ConsumerService {

    private final OrdersCheckoutService ordersCheckoutService;

    public PaymentAuthorizedConsumerService(OrdersCheckoutService ordersCheckoutService) {
        this.ordersCheckoutService = ordersCheckoutService;
    }

    @Override
    public void onPaymentAuthorizedEventV1(PaymentAuthorized payload, PaymentAuthorizedHeaders headers) {
        ordersCheckoutService.confirmOrder(
                new ConfirmOrderInput().setOrderId(payload.getOrderId().toString()));
    }
}
