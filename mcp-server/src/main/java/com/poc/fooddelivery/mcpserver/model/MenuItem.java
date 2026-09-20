package com.poc.fooddelivery.mcpserver.model;

public record MenuItem(
        String id,
        String name,
        int price,
        boolean vegetarian,
        boolean spicy,
        String description
) {
}
