package com.poc.fooddelivery.mcpserver.model;

import java.time.Instant;
import java.util.List;

public record Order(
        String id,
        String restaurantName,
        List<OrderItem> items,
        int total,
        String address,
        String paymentMethod,
        Instant placedAt
) {
}
