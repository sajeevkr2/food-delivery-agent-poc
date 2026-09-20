package com.poc.fooddelivery.guardrails;

import com.poc.fooddelivery.guardrails.support.ToolResultLog;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cleans up two ways a local Ollama model's tool-calling reply can leak raw
 * protocol artifacts into the final chat message instead of a natural
 * sentence:
 * <p>
 * 1. The model emits its intended tool call as plain text (e.g.
 * {"name":"searchDishes","arguments":{...}}) instead of using Ollama's
 * structured tool_calls field - sometimes several, one per line, when it
 * means to make multiple calls in sequence. Spring AI never sees these as
 * real tool invocations, so it's left to improvise with no real data - this
 * detects each leaked line and dispatches it to the matching ToolCallback
 * (local method or, as here, an MCP tool discovered from the food-delivery
 * MCP server) directly.
 * <p>
 * 2. After a real MCP tool call, the model echoes the tool result's raw MCP
 * content-block array (e.g. [{"text":"..."}]) instead of summarizing it -
 * this unwraps that array back to plain text. Since dispatching a leaked
 * call itself returns that same raw structure, both passes run per line,
 * not once over the whole reply.
 */
@Component
public class ToolCallLeakGuard {

    private final ToolCallbackProvider toolCallbackProvider;
    private final ToolResultLog toolResultLog;
    // Local models sometimes echo tool-call JSON / tool-result content with
    // real newline characters left un-escaped inside string values, which
    // strict JSON parsing rejects - tolerate that instead of treating it as
    // "not JSON" and giving up.
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    public ToolCallLeakGuard(ToolCallbackProvider toolCallbackProvider, ToolResultLog toolResultLog) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.toolResultLog = toolResultLog;
    }

    public String clean(String content) {
        if (content == null) {
            return null;
        }
        String[] lines = content.trim().split("\n");
        List<String> results = new ArrayList<>();
        boolean anyChanged = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String afterDispatch = tryDispatchLeakedCall(line).orElse(line);
            String finalPiece = tryUnwrapContentEcho(afterDispatch).orElse(afterDispatch);
            if (!finalPiece.equals(line)) {
                anyChanged = true;
            }
            results.add(finalPiece);
        }

        // Only replace the original text if something was actually leaked and
        // fixed - otherwise return it verbatim, preserving exact formatting.
        return anyChanged ? String.join("\n", results) : content;
    }

    private Optional<String> tryDispatchLeakedCall(String line) {
        if (!line.startsWith("{") || !line.contains("\"name\"")) {
            return Optional.empty();
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(line);
        } catch (Exception e) {
            return Optional.empty();
        }
        if (!node.has("name") || !node.has("arguments")) {
            return Optional.empty();
        }

        String name = node.get("name").asText();
        String argumentsJson = node.get("arguments").toString();

        for (ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
            if (callback.getToolDefinition().name().equals(name)) {
                String result = callback.call(argumentsJson);
                toolResultLog.record(result);
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    private Optional<String> tryUnwrapContentEcho(String line) {
        if (!line.startsWith("[")) {
            return Optional.empty();
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(line);
        } catch (Exception e) {
            return Optional.empty();
        }
        if (!node.isArray() || node.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode element : node) {
            if (!element.has("text")) {
                return Optional.empty();
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(element.get("text").asText());
        }
        return Optional.of(sb.toString());
    }
}
