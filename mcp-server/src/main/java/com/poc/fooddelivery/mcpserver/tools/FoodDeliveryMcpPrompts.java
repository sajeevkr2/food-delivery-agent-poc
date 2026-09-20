package com.poc.fooddelivery.mcpserver.tools;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A third MCP primitive alongside tools and resources: a reusable prompt
 * template any MCP client can fetch by name, pre-filled with arguments,
 * instead of every client having to hardcode this phrasing itself.
 */
@Component
public class FoodDeliveryMcpPrompts {

    @McpPrompt(
            name = "recommend_meal",
            description = "A meal recommendation prompt pre-filled with a dietary preference, spice level, and/or budget")
    public McpSchema.GetPromptResult recommendMeal(
            @McpArg(name = "preference", description = "Free-text preference, e.g. 'vegetarian and spicy' or 'under 200 rupees'", required = true)
            String preference) {

        String promptText = """
                I'm looking for something to eat. My preference: %s

                Search the available dishes that match this preference using the
                searchDishes tool, then recommend 2-3 real options with their
                restaurant, price, and a one-line reason why each fits. Only
                mention dishes the tool actually returned.
                """.formatted(preference);

        McpSchema.PromptMessage message = new McpSchema.PromptMessage(
                McpSchema.Role.USER, new McpSchema.TextContent(promptText));

        return McpSchema.GetPromptResult.builder(List.of(message))
                .description("A meal recommendation prompt pre-filled with the caller's preference")
                .build();
    }
}
