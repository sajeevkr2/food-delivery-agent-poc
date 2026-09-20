package com.poc.fooddelivery.mcpserver.service;

import com.poc.fooddelivery.mcpserver.model.OrderItem;
import com.poc.fooddelivery.mcpserver.model.Order;
import com.poc.fooddelivery.mcpserver.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates order progression by elapsed time instead of talking to a real
 * logistics/delivery-partner system, which isn't available for this POC.
 */
@Service
public class OrderService {

    private static final Duration CONFIRMED_UNTIL = Duration.ofSeconds(15);
    private static final Duration PREPARING_UNTIL = Duration.ofSeconds(40);
    private static final Duration OUT_FOR_DELIVERY_UNTIL = Duration.ofSeconds(70);

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private final AtomicInteger counter = new AtomicInteger(1000);

    public Order placeOrder(String restaurantName, List<OrderItem> items, int total, String address) {
        String id = "ORD" + counter.incrementAndGet();
        Order order = new Order(id, restaurantName, items, total, address, "COD", Instant.now());
        orders.put(id, order);
        return order;
    }

    public Optional<Order> getOrder(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    public enum CancelResult { CANCELLED, ALREADY_CANCELLED, TOO_LATE, NOT_FOUND }

    /** An order can be cancelled until it is out for delivery. */
    public CancelResult cancel(String id) {
        Order order = orders.get(id);
        if (order == null) {
            return CancelResult.NOT_FOUND;
        }
        OrderStatus status = computeStatus(order);
        if (status == OrderStatus.CANCELLED) {
            return CancelResult.ALREADY_CANCELLED;
        }
        if (status == OrderStatus.OUT_FOR_DELIVERY || status == OrderStatus.DELIVERED) {
            return CancelResult.TOO_LATE;
        }
        cancelled.add(id);
        return CancelResult.CANCELLED;
    }

    public OrderStatus computeStatus(Order order) {
        if (cancelled.contains(order.id())) {
            return OrderStatus.CANCELLED;
        }
        Duration elapsed = Duration.between(order.placedAt(), Instant.now());
        if (elapsed.compareTo(CONFIRMED_UNTIL) < 0) {
            return OrderStatus.CONFIRMED;
        }
        if (elapsed.compareTo(PREPARING_UNTIL) < 0) {
            return OrderStatus.PREPARING;
        }
        if (elapsed.compareTo(OUT_FOR_DELIVERY_UNTIL) < 0) {
            return OrderStatus.OUT_FOR_DELIVERY;
        }
        return OrderStatus.DELIVERED;
    }
}
