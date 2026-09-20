package com.poc.fooddelivery.mcpserver.dto;

import com.poc.fooddelivery.mcpserver.model.Order;
import com.poc.fooddelivery.mcpserver.model.OrderStatus;

import java.time.Instant;
import java.util.List;

public record OrderView(String id, String restaurantName, List<OrderItemView> items, int total,
                        String address, String paymentMethod, OrderStatus status, Instant placedAt) {
    public static OrderView from(Order order, OrderStatus status) {
        return new OrderView(
                order.id(),
                order.restaurantName(),
                order.items().stream().map(OrderItemView::from).toList(),
                order.total(),
                order.address(),
                order.paymentMethod(),
                status,
                order.placedAt());
    }
}
