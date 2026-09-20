package com.poc.fooddelivery.guardrails.support;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** Delegates to another ToolCallback and records each result in the ToolResultLog. */
public class RecordingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolResultLog log;

    public RecordingToolCallback(ToolCallback delegate, ToolResultLog log) {
        this.delegate = delegate;
        this.log = log;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        String result = delegate.call(toolInput);
        log.record(result);
        return result;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String result = delegate.call(toolInput, toolContext);
        log.record(result);
        return result;
    }
}
