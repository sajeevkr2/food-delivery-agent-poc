package com.poc.fooddelivery.mcpserver.tools;

import com.poc.fooddelivery.mcpserver.data.MockDataStore;
import com.poc.fooddelivery.mcpserver.model.MenuItem;
import com.poc.fooddelivery.mcpserver.model.Order;
import com.poc.fooddelivery.mcpserver.model.OrderItem;
import com.poc.fooddelivery.mcpserver.model.Restaurant;
import com.poc.fooddelivery.mcpserver.service.OrderService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exposed to any MCP client (this project's backend, or any other agent)
 * over the Model Context Protocol - see mcp-server's application.properties
 * for the Streamable-HTTP endpoint this is served on.
 */
@Component
public class FoodDeliveryMcpTools {

    private static final Pattern QTY_PREFIX = Pattern.compile("^(\\d+)\\s*[xX]?\\s*(\\D.*)$");
    private static final Pattern QTY_SUFFIX = Pattern.compile("^(.+?)\\s*[xX]\\s*(\\d+)$");

    private final OrderService orderService;

    public FoodDeliveryMcpTools(OrderService orderService) {
        this.orderService = orderService;
    }

    @McpTool(name = "searchRestaurants", description = "Search restaurants by name or cuisine (e.g. 'Indian', 'Chinese', 'Italian', 'Healthy'). Leave query empty to list all restaurants.")
    public String searchRestaurants(@McpToolParam(description = "Restaurant name or cuisine keyword, may be empty", required = false) String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<Restaurant> matches = MockDataStore.RESTAURANTS.stream()
                .filter(r -> q.isEmpty()
                        || r.name().toLowerCase().contains(q)
                        || r.cuisine().toLowerCase().contains(q))
                .toList();
        if (matches.isEmpty()) {
            return "No restaurants found matching '" + query + "'.";
        }
        StringBuilder sb = new StringBuilder();
        for (Restaurant r : matches) {
            sb.append(String.format("- %s (%s cuisine, rating %.1f)%n", r.name(), r.cuisine(), r.rating()));
        }
        return sb.toString();
    }

    @McpTool(name = "getMenu", description = "Get the full menu (dish names, prices, veg/spicy flags) for a restaurant by name.")
    public String getMenu(@McpToolParam(description = "Restaurant name", required = true) String restaurantName) {
        Optional<Restaurant> restaurant = findRestaurant(restaurantName);
        if (restaurant.isEmpty()) {
            return "No restaurant found matching '" + restaurantName + "'.";
        }
        StringBuilder sb = new StringBuilder("Menu for " + restaurant.get().name() + ":\n");
        for (MenuItem item : restaurant.get().menu()) {
            sb.append(String.format("- %s: Rs.%d (%s, %s) - %s%n",
                    item.name(), item.price(),
                    item.vegetarian() ? "veg" : "non-veg",
                    item.spicy() ? "spicy" : "mild",
                    item.description()));
        }
        return sb.toString();
    }

    @McpTool(name = "searchDishes", description = "Search dishes across all restaurants by dietary preference, spice level and/or max price. Any filter can be omitted.")
    public String searchDishes(
            @McpToolParam(description = "true for vegetarian only, false for non-veg only, omit for both", required = false) Boolean vegetarian,
            @McpToolParam(description = "true for spicy only, false for mild only, omit for both", required = false) Boolean spicy,
            @McpToolParam(description = "maximum price in rupees, omit for no limit", required = false) Integer maxPrice) {
        StringBuilder sb = new StringBuilder();
        for (Restaurant r : MockDataStore.RESTAURANTS) {
            for (MenuItem item : r.menu()) {
                if (vegetarian != null && item.vegetarian() != vegetarian) continue;
                if (spicy != null && item.spicy() != spicy) continue;
                if (maxPrice != null && item.price() > maxPrice) continue;
                sb.append(String.format("- %s from %s: Rs.%d (%s, %s) - %s%n",
                        item.name(), r.name(), item.price(),
                        item.vegetarian() ? "veg" : "non-veg",
                        item.spicy() ? "spicy" : "mild",
                        item.description()));
            }
        }
        return sb.isEmpty() ? "No dishes matched those filters." : sb.toString();
    }

    @McpTool(name = "findDishByName", description = "Find which restaurants serve specific dishes by name, e.g. 'Paneer Tikka, Naan'. Reports dishes that are not on any menu and which restaurants serve ALL the requested dishes. Use this when the user asks where to get particular dishes.")
    public String findDishByName(
            @McpToolParam(description = "One or more dish names, separated by commas or 'and'", required = true) String dishNames) {
        List<String> requested = java.util.Arrays.stream(dishNames.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(piece -> isKnownDish(piece)
                        ? java.util.stream.Stream.of(piece)
                        : java.util.Arrays.stream(piece.split("(?i)\\s+and\\s+")).map(String::trim).filter(s -> !s.isEmpty()))
                .toList();
        if (requested.isEmpty()) {
            return "No dish names were given.";
        }

        StringBuilder sb = new StringBuilder();
        java.util.Map<String, Integer> restaurantHits = new java.util.LinkedHashMap<>();
        for (String name : requested) {
            String q = name.toLowerCase();
            List<String> servedBy = new java.util.ArrayList<>();
            for (Restaurant r : MockDataStore.RESTAURANTS) {
                for (MenuItem item : r.menu()) {
                    if (item.name().toLowerCase().contains(q)) {
                        servedBy.add(r.name() + " (" + item.name() + ", Rs." + item.price() + ")");
                        restaurantHits.merge(r.name(), 1, Integer::sum);
                    }
                }
            }
            sb.append(servedBy.isEmpty()
                    ? "- " + name + ": not on any restaurant's menu.\n"
                    : "- " + name + ": " + String.join("; ", servedBy) + "\n");
        }

        List<String> servesAll = restaurantHits.entrySet().stream()
                .filter(e -> e.getValue() >= requested.size())
                .map(java.util.Map.Entry::getKey)
                .toList();
        if (requested.size() > 1) {
            sb.append(servesAll.isEmpty()
                    ? "No single restaurant serves all of: " + String.join(", ", requested) + ".\n"
                    : "Serves all of them: " + String.join(", ", servesAll) + ".\n");
        }
        return sb.toString();
    }

    @McpTool(name = "placeOrder", description = "Place a Cash on Delivery order. Only call this once the customer has agreed to the dishes AND given a delivery address. All dishes must be from the one restaurant. There is no cart: everything is given in this single call.")
    public String placeOrder(
            @McpToolParam(description = "Restaurant name", required = true) String restaurantName,
            @McpToolParam(description = "Dishes with optional quantities, comma separated, e.g. '2 Paneer Tikka, Dal Makhani'", required = true) String items,
            @McpToolParam(description = "Delivery address exactly as the customer gave it", required = true) String address) {
        if (address == null || address.isBlank()) {
            return "Order NOT placed: a delivery address is required.";
        }
        Optional<Restaurant> restaurant = findRestaurant(restaurantName);
        if (restaurant.isEmpty()) {
            return "Order NOT placed: no restaurant found matching '" + restaurantName + "'.";
        }

        List<OrderItem> lines = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String part : items == null ? new String[0] : items.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int quantity = 1;
            String dishName = entry;
            Matcher prefix = QTY_PREFIX.matcher(entry);
            Matcher suffix = QTY_SUFFIX.matcher(entry);
            if (prefix.matches()) {
                quantity = Integer.parseInt(prefix.group(1));
                dishName = prefix.group(2).trim();
            } else if (suffix.matches()) {
                dishName = suffix.group(1).trim();
                quantity = Integer.parseInt(suffix.group(2));
            }
            Optional<MenuItem> item = findMenuItem(restaurant.get(), dishName);
            if (item.isEmpty()) {
                missing.add(dishName);
            } else {
                lines.add(new OrderItem(item.get().name(), item.get().price(), Math.max(1, quantity)));
            }
        }

        if (!missing.isEmpty()) {
            return "Order NOT placed: these are not on " + restaurant.get().name() + "'s menu: "
                    + String.join(", ", missing) + ".";
        }
        if (lines.isEmpty()) {
            return "Order NOT placed: no dishes were given.";
        }

        int total = lines.stream().mapToInt(OrderItem::lineTotal).sum();
        Order order = orderService.placeOrder(restaurant.get().name(), lines, total, address.trim());

        StringBuilder sb = new StringBuilder("Order placed! Order ID is " + order.id() + ".\n");
        sb.append("Restaurant: ").append(order.restaurantName()).append("\n");
        for (OrderItem line : lines) {
            sb.append("- ").append(line.quantity()).append(" x ").append(line.itemName())
                    .append(": Rs.").append(line.lineTotal()).append("\n");
        }
        sb.append("Payment: Cash on Delivery - pay Rs.").append(total).append(" when it arrives.\n");
        sb.append("Delivering to: ").append(order.address()).append(".");
        return sb.toString();
    }

    @McpTool(name = "cancelOrder", description = "Cancel a previously placed order by order ID. Only possible until the order is out for delivery. Use when the customer asks to cancel.")
    public String cancelOrder(@McpToolParam(description = "Order ID, e.g. ORD1001", required = true) String orderId) {
        return switch (orderService.cancel(orderId)) {
            case CANCELLED -> "Order " + orderId + " has been cancelled. Nothing will be charged.";
            case ALREADY_CANCELLED -> "Order " + orderId + " was already cancelled.";
            case TOO_LATE -> "Order " + orderId + " can no longer be cancelled - it is already out for delivery or delivered.";
            case NOT_FOUND -> "No order found with ID '" + orderId + "'.";
        };
    }

    @McpTool(name = "checkOrderStatus",description = "Check the delivery status of a previously placed order by order ID.")
    public String checkOrderStatus(@McpToolParam(description = "Order ID, e.g. ORD1001", required = true) String orderId) {
        Optional<Order> order = orderService.getOrder(orderId);
        if (order.isEmpty()) {
            return "No order found with ID '" + orderId + "'.";
        }
        return "Order " + orderId + " status: " + orderService.computeStatus(order.get());
    }

    private boolean isKnownDish(String name) {
        String q = name.toLowerCase();
        return MockDataStore.RESTAURANTS.stream()
                .flatMap(r -> r.menu().stream())
                .anyMatch(i -> i.name().toLowerCase().contains(q));
    }

    private Optional<Restaurant> findRestaurant(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String q = name.trim().toLowerCase();
        return MockDataStore.RESTAURANTS.stream()
                .filter(r -> r.name().toLowerCase().contains(q))
                .findFirst();
    }

    private Optional<MenuItem> findMenuItem(Restaurant restaurant, String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return Optional.empty();
        }
        String q = itemName.trim().toLowerCase();
        return restaurant.menu().stream()
                .filter(i -> i.name().toLowerCase().contains(q))
                .findFirst();
    }
}
