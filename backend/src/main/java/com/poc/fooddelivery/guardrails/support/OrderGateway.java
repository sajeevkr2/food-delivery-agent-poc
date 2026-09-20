package com.poc.fooddelivery.guardrails.support;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** How the guards look up and cancel real orders on the MCP server. */
@Component
public class OrderGateway {

    public static final Pattern ORDER_ID = Pattern.compile("ORD\\d+");

    private final McpSyncClient mcpSyncClient;

    public OrderGateway(List<McpSyncClient> mcpSyncClients) {
        this.mcpSyncClient = mcpSyncClients.get(0);
    }

    /** The order://{id} resource as JSON, or {"error": ...} if there is no such order. */
    public String read(String orderId) {
        McpSchema.ReadResourceResult result = mcpSyncClient.readResource(
                new McpSchema.ReadResourceRequest("order://" + orderId));
        return ((McpSchema.TextResourceContents) result.contents().get(0)).text();
    }

    public static boolean isMissing(String orderJson) {
        return orderJson.startsWith("{\"error\"");
    }

    /** Calls the cancelOrder MCP tool and returns its plain-text answer. */
    public String cancel(String orderId) {
        McpSchema.CallToolResult result = mcpSyncClient.callTool(
                new McpSchema.CallToolRequest("cancelOrder", Map.of("orderId", orderId)));
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }
}
