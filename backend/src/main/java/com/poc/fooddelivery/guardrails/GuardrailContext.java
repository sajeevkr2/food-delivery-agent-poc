package com.poc.fooddelivery.guardrails;

import java.time.Instant;
import java.util.function.UnaryOperator;

/**
 * What a guard needs to know about the current chat turn.
 *
 * @param userMessage       what the customer just said
 * @param turnStart         when this turn began (to tell orders created now from old ones)
 * @param askAgainWithNote  asks the model again with an extra instruction appended to the
 *                          customer's message, returning its (already cleaned) new reply
 */
public record GuardrailContext(String userMessage, Instant turnStart, UnaryOperator<String> askAgainWithNote) {
}
