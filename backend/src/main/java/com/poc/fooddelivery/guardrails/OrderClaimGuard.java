package com.poc.fooddelivery.guardrails;

import com.poc.fooddelivery.guardrails.support.OrderGateway;
import com.poc.fooddelivery.guardrails.support.ToolResultLog;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catches "Order placed! Order ID is ORD1001" when no such order was placed.
 * The model sometimes never calls placeOrder and just guesses an ID, or reuses an
 * old (even cancelled) one. Any order ID in a reply must be one the customer
 * mentioned, or one created during this turn. If not, ask the model once more
 * with a direct instruction; if it still can't, tell the customer nothing was
 * ordered - with the real reason the order tool gave, when there was one.
 */
@Component
public class OrderClaimGuard implements Guardrail {

    private static final Pattern PLACED_AT = Pattern.compile("\"placedAt\":\"([^\"]+)\"");
    private static final String RETRY_NOTE =
            "\n\n(System note: your last reply said an order was placed, but the placeOrder tool was not "
                    + "called, so no order exists. Call the placeOrder tool now with the restaurant, dishes and "
                    + "delivery address from this conversation, and report only what it actually returns.)";

    private final OrderGateway orders;
    private final ToolResultLog toolResults;

    public OrderClaimGuard(OrderGateway orders, ToolResultLog toolResults) {
        this.orders = orders;
        this.toolResults = toolResults;
    }

    @Override
    public String apply(GuardrailContext context, String reply) {
        if (!claimsUnplacedOrder(reply, context)) {
            return reply;
        }
        String retried = context.askAgainWithNote().apply(RETRY_NOTE);
        return claimsUnplacedOrder(retried, context) ? couldNotPlaceMessage() : retried;
    }

    /** True if the reply names a nonexistent order, or one that is neither asked about nor new this turn. */
    private boolean claimsUnplacedOrder(String reply, GuardrailContext context) {
        Matcher matcher = OrderGateway.ORDER_ID.matcher(reply);
        while (matcher.find()) {
            String orderId = matcher.group();
            String json = orders.read(orderId);
            if (OrderGateway.isMissing(json)) {
                return true;
            }
            boolean customerAskedAboutIt = context.userMessage().contains(orderId);
            Matcher placedAt = PLACED_AT.matcher(json);
            if (!customerAskedAboutIt && placedAt.find()
                    && Instant.parse(placedAt.group(1)).isBefore(context.turnStart().minusSeconds(2))) {
                return true;
            }
        }
        return false;
    }

    /** Tells the customer the real reason the order tool refused, when there was one. */
    private String couldNotPlaceMessage() {
        String marker = "Order NOT placed:";
        String refusal = toolResults.lastContaining(marker);
        String reason = "";
        if (refusal != null) {
            // The result may still be wrapped in MCP's JSON, so cut the reason out by hand.
            reason = refusal.substring(refusal.indexOf(marker) + marker.length());
            int quote = reason.indexOf('"');
            if (quote >= 0) {
                reason = reason.substring(0, quote);
            }
            for (String instruction : new String[] {"Tell the customer", "Ask the customer"}) {
                int at = reason.indexOf(instruction);
                if (at >= 0) {
                    reason = reason.substring(0, at);
                }
            }
            reason = reason.trim();
        }
        return "Sorry, I couldn't place that order" + (reason.isEmpty() ? "." : ": " + reason)
                + " Nothing has been ordered and nothing will be charged. "
                + "Tell me the dishes, restaurant and delivery address you'd like and I'll try again.";
    }
}
