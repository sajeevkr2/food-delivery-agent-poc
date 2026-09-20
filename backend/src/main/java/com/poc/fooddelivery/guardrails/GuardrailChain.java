package com.poc.fooddelivery.guardrails;

import com.poc.fooddelivery.guardrails.support.ToolResultLog;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Everything that stands between the local model and the customer, in one place.
 * <ol>
 *   <li>{@link #sanitize} - repair the raw reply (tool calls printed as text,
 *       raw MCP JSON echoed back) - {@link ToolCallLeakGuard}</li>
 *   <li>{@link #verify} - check the claims in it against the real system, in order:
 *       {@link OrderClaimGuard}, {@link DishClaimGuard}, {@link CancelGuard}</li>
 * </ol>
 * The system prompt rules (ChatClientConfig) and the tool-side validation in
 * mcp-server (e.g. placeOrder refusing an unknown dish) are the other two layers.
 */
@Component
public class GuardrailChain {

    private final ToolCallLeakGuard leakGuard;
    private final ToolResultLog toolResults;
    private final List<Guardrail> verifiers;

    public GuardrailChain(ToolCallLeakGuard leakGuard, ToolResultLog toolResults,
                          OrderClaimGuard orderClaimGuard, DishClaimGuard dishClaimGuard, CancelGuard cancelGuard) {
        this.leakGuard = leakGuard;
        this.toolResults = toolResults;
        this.verifiers = List.of(orderClaimGuard, dishClaimGuard, cancelGuard);
    }

    /** Call at the start of every chat turn: forget the previous turn's tool results. */
    public void startTurn() {
        toolResults.start();
    }

    /** Repairs a raw model reply so the rest of the guards see plain text. */
    public String sanitize(String rawReply) {
        return leakGuard.clean(rawReply);
    }

    /** Runs every verifying guard in order; each may replace the reply. */
    public String verify(GuardrailContext context, String reply) {
        String current = reply;
        for (Guardrail guard : verifiers) {
            current = guard.apply(context, current);
        }
        return current;
    }
}
