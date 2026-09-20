package com.poc.fooddelivery.web;

import com.poc.fooddelivery.dto.ChatRequestDto;
import com.poc.fooddelivery.dto.ChatResponseDto;
import com.poc.fooddelivery.dto.RecommendRequestDto;
import com.poc.fooddelivery.guardrails.GuardrailChain;
import com.poc.fooddelivery.guardrails.GuardrailContext;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final GuardrailChain guardrails;
    private final McpSyncClient mcpSyncClient;
    private final List<Message> history = Collections.synchronizedList(new ArrayList<>());

    public ChatController(ChatClient chatClient, GuardrailChain guardrails, List<McpSyncClient> mcpSyncClients) {
        this.chatClient = chatClient;
        this.guardrails = guardrails;
        this.mcpSyncClient = mcpSyncClients.get(0);
    }

    @PostMapping
    public ChatResponseDto chat(@RequestBody ChatRequestDto request) {
        return new ChatResponseDto(sendToAgent(request.message()));
    }

    @PostMapping("/recommend")
    public ChatResponseDto recommend(@RequestBody RecommendRequestDto request) {
        // Fetches the food-delivery MCP server's "recommend_meal" prompt template
        // (an @McpPrompt) instead of hardcoding this phrasing here, so the prompt
        // stays owned and versioned by the service that knows the domain.
        McpSchema.GetPromptResult prompt = mcpSyncClient.getPrompt(
                new McpSchema.GetPromptRequest("recommend_meal", Map.of("preference", request.preference())));
        String promptText = ((McpSchema.TextContent) prompt.messages().get(0).content()).text();

        return new ChatResponseDto(sendToAgent(promptText));
    }

    @DeleteMapping
    public void reset() {
        history.clear();
    }

    private String sendToAgent(String userMessage) {
        List<Message> priorMessages;
        synchronized (history) {
            priorMessages = new ArrayList<>(history);
        }

        guardrails.startTurn();
        Instant turnStart = Instant.now(); // before the model runs, so orders it places count as "this turn"
        String reply = callAgent(priorMessages, userMessage);

        // Never trust the model's word for it: verify what the reply claims (orders,
        // dishes and prices, cancellations) against the real system. See guardrails/.
        GuardrailContext context = new GuardrailContext(userMessage, turnStart,
                note -> callAgent(priorMessages, userMessage + note));
        reply = guardrails.verify(context, reply);

        history.add(new UserMessage(userMessage));
        history.add(new AssistantMessage(reply));

        return reply;
    }

    private String callAgent(List<Message> priorMessages, String userMessage) {
        String reply = chatClient.prompt()
                .messages(priorMessages)
                .user(userMessage)
                .call()
                .content();
        return guardrails.sanitize(reply);
    }
}
