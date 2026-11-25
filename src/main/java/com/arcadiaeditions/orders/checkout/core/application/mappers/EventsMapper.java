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

    com.arcadiaeditions.orders.checkout.events.avro.OrderConfirmed asOrderConfirmed(Order order);

    com.arcadiaeditions.orders.checkout.events.avro.OrderCancelled asOrderCancelled(Order order);

    com.arcadiaeditions.orders.checkout.events.avro.StockUnavailable asStockUnavailable(Order order);
}
