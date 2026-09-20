package com.poc.fooddelivery.web;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Order state lives in the food-delivery MCP server, exposed as an MCP
 * Resource (order://{orderId}) rather than REST - this reads it over the
 * same MCP connection the chat agent uses, and republishes it as plain JSON
 * for the UI's order-status card.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final McpSyncClient mcpSyncClient;

    public OrderController(List<McpSyncClient> mcpSyncClients) {
        this.mcpSyncClient = mcpSyncClients.get(0);
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> getOrder(@PathVariable String id) {
        McpSchema.ReadResourceResult result = mcpSyncClient.readResource(
                new McpSchema.ReadResourceRequest("order://" + id));
        String json = ((McpSchema.TextResourceContents) result.contents().get(0)).text();

        // The MCP resource reports "not found" as an {"error": ...} body; surface
        // it as a real 404 so the UI can tell a missing order from a real one.
        if (json.startsWith("{\"error\"")) {
            return ResponseEntity.status(404).contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    /** Cancels via the MCP cancelOrder tool - the same tool the chat agent can call. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> cancelOrder(@PathVariable String id) {
        McpSchema.CallToolResult result = mcpSyncClient.callTool(
                new McpSchema.CallToolRequest("cancelOrder", Map.of("orderId", id)));
        String message = ((McpSchema.TextContent) result.content().get(0)).text();
        boolean cancelled = message.contains("has been cancelled") || message.contains("already cancelled");
        return ResponseEntity.status(cancelled ? 200 : 409)
                .body(Map.of("cancelled", cancelled, "message", message));
    }
}
