package com.poc.fooddelivery.guardrails;

/**
 * One check on the model's reply. A small local model will announce orders it
 * never placed, invent dishes and prices, and claim cancellations it never made,
 * so each guard verifies one kind of claim against the real system (via MCP)
 * instead of trusting the wording. Guards run in order in {@link GuardrailChain};
 * each gets the reply so far and returns the reply to use - the same one if
 * nothing was wrong.
 */
public interface Guardrail {

    String apply(GuardrailContext context, String reply);
}
