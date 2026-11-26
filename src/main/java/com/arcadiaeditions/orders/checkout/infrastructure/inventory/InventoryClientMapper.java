package com.arcadiaeditions.orders.checkout.infrastructure.inventory;

import com.arcadiaeditions.orders.checkout.clients.inventory.dtos.ReserveStockInputDTO;
import com.arcadiaeditions.orders.checkout.clients.inventory.dtos.ReserveStockItemInputDTO;
import com.arcadiaeditions.orders.checkout.clients.inventory.dtos.StockReservationConfirmedResultDTO;
import com.arcadiaeditions.orders.checkout.clients.inventory.dtos.StockReservedLineDTO;
import com.arcadiaeditions.orders.checkout.clients.inventory.dtos.StockReservedResultDTO;
import com.arcadiaeditions.orders.checkout.clients.inventory.dtos.StockUnavailableLineDTO;
import com.arcadiaeditions.orders.checkout.clients.inventory.dtos.StockUnavailableResultDTO;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.ReserveStockItem;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservationConfirmedResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockReservedResult;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.StockUnavailableResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Port types of {@link com.arcadiaeditions.orders.checkout.core.outbound.inventory.InventoryClient} to and from the
 * generated catalog-inventory-api DTOs. A response without orderId keeps the requested one.
 */
@Mapper
public interface InventoryClientMapper {

    InventoryClientMapper INSTANCE = Mappers.getMapper(InventoryClientMapper.class);

    ReserveStockInputDTO asReserveStockInput(String orderId, List<ReserveStockItem> items);

    ReserveStockItemInputDTO asReserveStockItemInput(ReserveStockItem item);

    @Mapping(target = "orderId", expression = "java(orderIdOr(reserved.getOrderId(), requestedOrderId))")
    StockReservedResult asStockReservedResult(StockReservedResultDTO reserved, String requestedOrderId);

    StockReservedResult.StockReservedLine asStockReservedLine(StockReservedLineDTO line);

    @Mapping(target = "orderId", expression = "java(orderIdOr(unavailable.getOrderId(), requestedOrderId))")
    StockUnavailableResult asStockUnavailableResult(StockUnavailableResultDTO unavailable, String requestedOrderId);

    StockUnavailableResult.StockUnavailableLine asStockUnavailableLine(StockUnavailableLineDTO line);

    @Mapping(target = "orderId", expression = "java(orderIdOr(confirmed.getOrderId(), requestedOrderId))")
    StockReservationConfirmedResult asStockReservationConfirmedResult(
            StockReservationConfirmedResultDTO confirmed, String requestedOrderId);

    default Instant asInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    default String orderIdOr(String fromResponse, String requested) {
        return fromResponse != null ? fromResponse : requested;
    }
}
