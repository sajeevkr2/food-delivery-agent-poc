package com.poc.fooddelivery.guardrails;

import com.poc.fooddelivery.guardrails.support.OrderGateway;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the dishes and prices a reply states against the real menu (read from
 * the MCP server's menu://all resource). A small local model will happily invent
 * a dish or a price, so instead of trusting the wording, any line that names a
 * dish that doesn't exist, or gives a price the dish doesn't have, is replaced by
 * a plain correction built from the real menu.
 */
@Component
public class DishClaimGuard implements Guardrail {

    private record Entry(String restaurant, String dish, int price) {
    }

    private static final Pattern PRICE = Pattern.compile("Rs\\.?\\s*(\\d+)");
    // "- Naan: Rs.120", "2 x Naan (Rs.100)", "Naan from X: Rs.50" -> the leading dish-like name
    private static final Pattern LEADING_NAME =
            Pattern.compile("^[\\s\\-*\\u2022\\d.xX]*([A-Za-z][A-Za-z' ]{1,40}?)\\s*(?::|\\(| - | from | at )");
    // Lines about totals, fees or payment are not dish claims.
    private static final Pattern NOT_A_DISH = Pattern.compile(
            "(?i)total|pay|fee|charge|amount|cash|delivery|cart|order|bill|price|budget|under|within|address|restaurant|status");

    private final McpSyncClient mcpSyncClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile List<Entry> catalog;

    public DishClaimGuard(List<McpSyncClient> mcpSyncClients) {
        this.mcpSyncClient = mcpSyncClients.get(0);
    }

    @Override
    public String apply(GuardrailContext context, String reply) {
        // A reply that names an order is a confirmation (OrderClaimGuard's job), not a menu quote.
        if (OrderGateway.ORDER_ID.matcher(reply).find()) {
            return reply;
        }
        return correction(reply, context.userMessage()).orElse(reply);
    }

    /** A corrected reply if the given one states a dish or price that isn't on the real menu. */
    public Optional<String> correction(String reply, String userMessage) {
        List<Entry> menu = loadCatalog();
        if (menu.isEmpty() || reply == null) {
            return Optional.empty();
        }

        Set<String> unknownDishes = new LinkedHashSet<>();
        Set<String> wrongPrices = new LinkedHashSet<>();

        for (String line : reply.split("\n")) {
            Matcher price = PRICE.matcher(line);
            if (!price.find()) {
                continue;
            }
            int stated = Integer.parseInt(price.group(1));
            String lower = line.toLowerCase();

            List<Entry> named = menu.stream()
                    .filter(e -> lower.contains(e.dish().toLowerCase()))
                    .toList();
            if (!named.isEmpty()) {
                // A line total for several portions is fine (2 x Rs.249 = Rs.498).
                boolean plausible = named.stream().anyMatch(e -> stated % e.price() == 0);
                if (!plausible) {
                    named.stream().map(e -> e.dish() + " costs Rs." + e.price() + " at " + e.restaurant())
                            .distinct().forEach(wrongPrices::add);
                }
                continue;
            }

            Matcher name = LEADING_NAME.matcher(line);
            if (name.find()) {
                String candidate = name.group(1).trim();
                if (!NOT_A_DISH.matcher(candidate).find()) {
                    unknownDishes.add(candidate);
                }
            }
        }

        if (unknownDishes.isEmpty() && wrongPrices.isEmpty()) {
            return Optional.empty();
        }

        List<String> parts = new ArrayList<>();
        if (!unknownDishes.isEmpty()) {
            parts.add("I can't offer " + String.join(", ", unknownDishes.stream().map(d -> "'" + d + "'").toList())
                    + ": not on any restaurant's menu.");
        }
        if (!wrongPrices.isEmpty()) {
            parts.add("Correct prices: " + String.join("; ", wrongPrices) + ".");
        }
        // Keep what IS available among the dishes mentioned, so the customer isn't left empty-handed.
        String mentioned = (reply + " " + userMessage).toLowerCase();
        List<String> available = menu.stream()
                .filter(e -> mentioned.contains(e.dish().toLowerCase()))
                .map(e -> e.dish() + " (" + e.restaurant() + ", Rs." + e.price() + ")")
                .distinct()
                .toList();
        if (!unknownDishes.isEmpty() && !available.isEmpty()) {
            parts.add("Available: " + String.join("; ", available) + ". Want just those, or something else?");
        } else {
            parts.add("Tell me what you'd like instead.");
        }
        return Optional.of(String.join(" ", parts));
    }

    private List<Entry> loadCatalog() {
        List<Entry> cached = catalog;
        if (cached != null) {
            return cached;
        }
        try {
            McpSchema.ReadResourceResult result = mcpSyncClient.readResource(new McpSchema.ReadResourceRequest("menu://all"));
            JsonNode array = objectMapper.readTree(((McpSchema.TextResourceContents) result.contents().get(0)).text());
            List<Entry> entries = new ArrayList<>();
            for (JsonNode node : array) {
                entries.add(new Entry(node.get("restaurant").asText(), node.get("dish").asText(), node.get("price").asInt()));
            }
            catalog = entries;
            return entries;
        } catch (RuntimeException e) {
            // Never break chat just because the check couldn't run.
            return List.of();
        }
    }
}
