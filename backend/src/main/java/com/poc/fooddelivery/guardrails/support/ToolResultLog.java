package com.poc.fooddelivery.guardrails.support;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers what tools actually returned during the current chat request, so if
 * the model then misreports it (or a guard has to reject its reply) the customer
 * can be told the real reason instead of a generic failure.
 */
@Component
public class ToolResultLog {

    private final ThreadLocal<List<String>> results = ThreadLocal.withInitial(ArrayList::new);

    public void start() {
        results.get().clear();
    }

    public void record(String result) {
        if (result != null) {
            results.get().add(result);
        }
    }

    /** The most recent tool result containing the given text, if any (MCP wraps results in JSON). */
    public String lastContaining(String text) {
        List<String> all = results.get();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).contains(text)) {
                return all.get(i);
            }
        }
        return null;
    }
}
