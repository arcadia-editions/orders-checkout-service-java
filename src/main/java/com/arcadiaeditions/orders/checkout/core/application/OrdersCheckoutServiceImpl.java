package com.arcadiaeditions.orders.checkout.core.application;

import com.arcadiaeditions.orders.checkout.asyncapi.provider.*;
import com.arcadiaeditions.orders.checkout.core.application.mappers.*;
import com.arcadiaeditions.orders.checkout.core.domain.*;
import com.arcadiaeditions.orders.checkout.core.inbound.*;
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*;
import com.arcadiaeditions.orders.checkout.core.outbound.ids.OrderIdGenerator;
import com.arcadiaeditions.orders.checkout.core.outbound.inventory.*;
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
    private final Clock clock;
    private final InventoryClient inventoryClient;
    private final OrderIdGenerator orderIdGenerator;

    /**
     * Constructor.
     */
    public OrdersCheckoutServiceImpl(
            OrderRepository orderRepository,
            OrdersCheckoutEventsProducer eventsProducer,
            Clock clock,
            InventoryClient inventoryClient,
            OrderIdGenerator orderIdGenerator) {
        this.orderRepository = orderRepository;
        this.eventsProducer = eventsProducer;
        this.clock = clock;
        this.inventoryClient = inventoryClient;
        this.orderIdGenerator = orderIdGenerator;
    }

    /** Clock used for createdAt, confirmedAt, cancelledAt and event timestamps such as rejectedAt. */
    Clock clock() {
        return clock;
    }

    public Order startOrderCheckout(StartOrderCheckoutInput input) {

        log.debug("Request startOrderCheckout: {}", input);

        var orderId = orderIdGenerator.nextOrderId();
        var reservation = inventoryClient.reserveStock(
                orderId,
                input.getItems().stream()
                        .map(item -> new ReserveStockItem(item.getSku(), item.getQuantity()))
                        .toList());
        if (!(reservation instanceof StockReservedResult)) {
            // StockUnavailable (409, nothing persisted, event StockUnavailable) is task T-10
            throw new UnsupportedOperationException("StockUnavailable branch of startOrderCheckout: task T-10");
        }

        var order = ordersCheckoutServiceMapper.update(new Order(), input);
        order.setOrderId(orderId);
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(Instant.now(clock));
        order.setTotalAmount(input.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        order = orderRepository.save(order);
        // emit events
        var orderCreated = eventsMapper.asOrderCreated(order);
        eventsProducer.onOrderCreated(orderCreated);
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
