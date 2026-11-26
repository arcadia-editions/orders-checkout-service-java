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

    public Order startOrderCheckout(StartOrderCheckoutInput input) {

        log.debug("Request startOrderCheckout: {}", input);

        var orderId = orderIdGenerator.nextOrderId();
        var reservation = inventoryClient.reserveStock(
                orderId,
                input.getItems().stream()
                        .map(item -> new ReserveStockItem(item.getSku(), item.getQuantity()))
                        .toList());
        if (reservation instanceof StockUnavailableResult unavailable) {
            // nothing persisted: only the event, and the exception the web adapter answers 409 from
            eventsProducer.onStockUnavailable(
                    eventsMapper.asStockUnavailable(input, orderId, unavailable.unavailableItems()));
            throw new StockUnavailableException(
                    orderId,
                    unavailable.unavailableItems().stream()
                            .map(line -> new StockUnavailableException.Line(
                                    line.sku(), line.requestedQuantity(), line.availableQuantity()))
                            .toList());
        }

        var order = ordersCheckoutServiceMapper.update(new Order(), input);
        order.setOrderId(orderId);
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(Instant.now(clock));
        order.setTotalAmount(input.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        order = orderRepository.saveAndFlush(order);
        // emit events
        var orderCreated = eventsMapper.asOrderCreated(order);
        eventsProducer.onOrderCreated(orderCreated);
        return order;
    }

    public Order confirmOrder(ConfirmOrderInput input) {

        log.debug("Request confirmOrder: {}", input);

        var existing = orderRepository.findByOrderId(input.getOrderId()).orElseThrow();
        if (existing.getStatus() == OrderStatus.CONFIRMED) {
            // redelivery of PaymentAuthorized: no inventory call, no event
            return existing;
        }
        if (existing.getStatus() == OrderStatus.CANCELLED) {
            return rejectConfirmation(existing, "ORDER_CANCELLED");
        }
        OrderTransitions.ensureCanConfirmOrder(existing);
        var confirmation = inventoryClient.confirmStockReservation(existing.getOrderId());
        if (!(confirmation instanceof StockReservationConfirmedResult)) {
            return rejectConfirmation(existing, "STOCK_NOT_HELD");
        }
        existing.setStatus(OrderStatus.CONFIRMED);
        existing.setConfirmedAt(Instant.now(clock));
        var order = orderRepository.saveAndFlush(existing);
        // emit events
        var orderConfirmed = eventsMapper.asOrderConfirmed(order);
        eventsProducer.onOrderConfirmed(orderConfirmed);
        return order;
    }

    private Order rejectConfirmation(Order order, String reason) {
        // no state change: only the event asking payments to void the late authorization
        var rejected = eventsMapper.asOrderConfirmationRejected(order, reason, Instant.now(clock));
        eventsProducer.onOrderConfirmationRejected(rejected);
        return order;
    }

    public Order cancelOrder(CancelOrderInput input) {

        log.debug("Request cancelOrder: {}", input);

        var existing = orderRepository.findByOrderId(input.getOrderId()).orElseThrow();
        if (existing.getStatus() == OrderStatus.CANCELLED) {
            // redelivery of StockReleased: no change, no event
            return existing;
        }
        OrderTransitions.ensureCanCancelOrder(existing);
        existing.setStatus(OrderStatus.CANCELLED);
        existing.setCancellationReason(input.getReason());
        existing.setCancelledAt(Instant.now(clock));
        var order = orderRepository.saveAndFlush(existing);
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
