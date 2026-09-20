package com.poc.fooddelivery.guardrails;

import com.poc.fooddelivery.guardrails.support.OrderGateway;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When the customer explicitly asks to cancel a named order, the model sometimes
 * says "Order cancelled" without calling the tool. If the order isn't really
 * CANCELLED afterwards, call the same cancelOrder MCP tool directly and report
 * its real answer (cancelled, too late, or not found).
 */
@Component
public class CancelGuard implements Guardrail {

    private static final Pattern CANCEL_INTENT = Pattern.compile("(?i)\\bcancel");
    private static final Pattern STATUS = Pattern.compile("\"status\":\"([A-Z_]+)\"");

    private final OrderGateway orders;

    public CancelGuard(OrderGateway orders) {
        this.orders = orders;
    }

    @Override
    public String apply(GuardrailContext context, String reply) {
        String userMessage = context.userMessage();
        if (!CANCEL_INTENT.matcher(userMessage).find()) {
            return reply;
        }
        Matcher id = OrderGateway.ORDER_ID.matcher(userMessage);
        if (!id.find()) {
            return reply;
        }
        String orderId = id.group();
        Matcher status = STATUS.matcher(orders.read(orderId));
        if (status.find() && status.group(1).equals("CANCELLED")) {
            return reply;
        }
        return orders.cancel(orderId);
    }
}
