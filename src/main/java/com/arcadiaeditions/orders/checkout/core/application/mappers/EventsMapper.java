package com.arcadiaeditions.orders.checkout.core.application.mappers;

import com.arcadiaeditions.orders.checkout.core.domain.*;
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {BaseMapper.class})
public interface EventsMapper {

    EventsMapper INSTANCE = Mappers.getMapper(EventsMapper.class);

    @Mapping(target = "amount", source = "totalAmount")
    com.arcadiaeditions.orders.checkout.events.avro.OrderCreated asOrderCreated(Order order);

    @Mapping(target = "shippingAddressBuilder", ignore = true)
    com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmed asOrderConfirmed(Order order);

    @Mapping(target = "reason", source = "reason")
    @Mapping(target = "rejectedAt", source = "rejectedAt")
    com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmationRejected asOrderConfirmationRejected(
            Order order, String reason, java.time.Instant rejectedAt);

    @Mapping(target = "reason", source = "cancellationReason")
    com.arcadiaeditions.orders.checkout.events.avro.OrderCancelled asOrderCancelled(Order order);

    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "customerId", source = "input.customerId")
    @Mapping(target = "customerEmail", source = "input.customerEmail")
    @Mapping(target = "unavailableItems", source = "unavailableItems")
    com.arcadiaeditions.orders.checkout.events.avro.StockUnavailable asStockUnavailable(
            StartOrderCheckoutInput input,
            String orderId,
            java.util.List<
                            com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockUnavailableResult
                                    .StockUnavailableLine>
                    unavailableItems);
}
