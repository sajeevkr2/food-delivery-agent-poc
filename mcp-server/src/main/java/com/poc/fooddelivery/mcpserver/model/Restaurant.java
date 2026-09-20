package com.poc.fooddelivery.mcpserver.model;

import java.util.List;

public record Restaurant(
        String id,
        String name,
        String cuisine,
        double rating,
        List<MenuItem> menu
) {
}
