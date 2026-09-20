package com.poc.fooddelivery.mcpserver.dto;

import com.poc.fooddelivery.mcpserver.model.OrderItem;

public record OrderItemView(String itemName, int unitPrice, int quantity, int lineTotal) {
    public static OrderItemView from(OrderItem item) {
        return new OrderItemView(item.itemName(), item.unitPrice(), item.quantity(), item.lineTotal());
    }
}
