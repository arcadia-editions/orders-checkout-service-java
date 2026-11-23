package com.arcadiaeditions.orders.checkout.core.application;

import com.arcadiaeditions.orders.checkout.asyncapi.provider.*;
import com.arcadiaeditions.orders.checkout.core.application.mappers.*;
import com.arcadiaeditions.orders.checkout.core.domain.*;
import com.arcadiaeditions.orders.checkout.core.inbound.*;
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*;
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.*;
// import com.arcadiaeditions.orders.checkout.events.avro.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing [Order].
 */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
public class OrdersCheckoutServiceImpl implements OrdersCheckoutService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final OrdersCheckoutServiceMapper ordersCheckoutServiceMapper = OrdersCheckoutServiceMapper.INSTANCE;

    private final OrderRepository orderRepository;

    private final EventsMapper eventsMapper = EventsMapper.INSTANCE;
    private final OrdersCheckoutEventsProducer eventsProducer;

    /**
     * Constructor.
     */
    public OrdersCheckoutServiceImpl(OrderRepository orderRepository, OrdersCheckoutEventsProducer eventsProducer) {
        this.orderRepository = orderRepository;
        this.eventsProducer = eventsProducer;
    }

    public Order startOrderCheckout(StartOrderCheckoutInput input) {
        // TODO: call CatalogInventoryZdl InventoryService.reserveStock(ReserveStockInput) -> ReserveStockResult

        log.debug("Request startOrderCheckout: {}", input);

        var order = ordersCheckoutServiceMapper.update(new Order(), input);
        // TODO: implement this method
        // emit events
        var orderCreated = eventsMapper.asOrderCreated(order);
        eventsProducer.onOrderCreated(orderCreated);
        var stockUnavailable = eventsMapper.asStockUnavailable(order);
        eventsProducer.onStockUnavailable(stockUnavailable);
        return order;
    }

    public Order confirmOrder(ConfirmOrderInput input) {
        // TODO: call CatalogInventoryZdl InventoryService.confirmStockReservation(ConfirmStockReservationInput) ->
        // StockReservation
        //       then emit one of: StockReservationConfirmed

        log.debug("Request confirmOrder: {}", input);

        // TODO CUSTOM_REQUIRED: load the Order this transition applies to, e.g. by a correlation key in the input
        Optional<Order> foundOrder = Optional.empty();
        var order = foundOrder
                .map(existingOrder -> {
                    OrderTransitions.ensureCanConfirmOrder(existingOrder);
                    ordersCheckoutServiceMapper.update(existingOrder, input);
                    existingOrder.setStatus(OrderStatus.CONFIRMED);
                    return existingOrder;
                })
                .map(orderRepository::save)
                .orElseThrow();
        // emit events
        var orderConfirmed = eventsMapper.asOrderConfirmed(order);
        eventsProducer.onOrderConfirmed(orderConfirmed);
        return order;
    }

    public Order cancelOrder(CancelOrderInput input) {

        log.debug("Request cancelOrder: {}", input);

        // TODO CUSTOM_REQUIRED: load the Order this transition applies to, e.g. by a correlation key in the input
        Optional<Order> foundOrder = Optional.empty();
        var order = foundOrder
                .map(existingOrder -> {
                    OrderTransitions.ensureCanCancelOrder(existingOrder);
                    ordersCheckoutServiceMapper.update(existingOrder, input);
                    existingOrder.setStatus(OrderStatus.CANCELLED);
                    return existingOrder;
                })
                .map(orderRepository::save)
                .orElseThrow();
        // emit events
        var orderCancelled = eventsMapper.asOrderCancelled(order);
        eventsProducer.onOrderCancelled(orderCancelled);
        return order;
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrder(Long id) {

        log.debug("[CRUD] Request to get Order : {}", id);
        var order = orderRepository.findById(id);
        return order;
    }
}
