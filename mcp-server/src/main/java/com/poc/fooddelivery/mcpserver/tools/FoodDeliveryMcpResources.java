package com.poc.fooddelivery.mcpserver.tools;

import com.poc.fooddelivery.mcpserver.data.MockDataStore;
import com.poc.fooddelivery.mcpserver.dto.OrderView;
import com.poc.fooddelivery.mcpserver.model.Order;
import com.poc.fooddelivery.mcpserver.service.OrderService;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

/**
 * Exposes order state as an MCP Resource instead of plain REST, so this
 * server's public surface stays 100% MCP (tools + resources + prompts).
 * backend reads it over its existing MCP connection and republishes it as
 * REST for the UI's order-status card.
 */
@Component
public class FoodDeliveryMcpResources {

    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FoodDeliveryMcpResources(OrderService orderService) {
        this.orderService = orderService;
    }

    public record MenuEntry(String restaurant, String dish, int price) {
    }

    @McpResource(
            uri = "menu://all",
            name = "Full menu",
            description = "Every dish on every restaurant's menu with its price, as a flat JSON list",
            mimeType = "application/json")
    public McpSchema.ReadResourceResult getFullMenu() {
        List<MenuEntry> entries = MockDataStore.RESTAURANTS.stream()
                .flatMap(r -> r.menu().stream().map(i -> new MenuEntry(r.name(), i.name(), i.price())))
                .toList();
        return McpSchema.ReadResourceResult.builder(List.of(
                McpSchema.TextResourceContents.builder("menu://all", objectMapper.writeValueAsString(entries))
                        .mimeType("application/json")
                        .build()
        )).build();
    }

    @McpResource(
            uri = "order://{orderId}",
            name = "Order",
            description = "A placed order's items, total, delivery address, payment method and live delivery status",
            mimeType = "application/json")
    public McpSchema.ReadResourceResult getOrder(String orderId) {
        Optional<Order> order = orderService.getOrder(orderId);
        String json = order.isPresent()
                ? objectMapper.writeValueAsString(OrderView.from(order.get(), orderService.computeStatus(order.get())))
                : "{\"error\":\"No order found with ID '" + orderId + "'.\"}";
        return McpSchema.ReadResourceResult.builder(List.of(
                McpSchema.TextResourceContents.builder("order://" + orderId, json)
                        .mimeType("application/json")
                        .build()
        )).build();
    }
}
