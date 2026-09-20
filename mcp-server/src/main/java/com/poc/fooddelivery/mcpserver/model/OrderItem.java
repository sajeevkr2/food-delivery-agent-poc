package com.poc.fooddelivery.mcpserver.model;

public record OrderItem(String itemName, int unitPrice, int quantity) {
    public int lineTotal() {
        return unitPrice * quantity;
    }
}
