package dev.aiengineer.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * Answer of a model that may call tools: either text, or tool calls the caller has to run.
 * The provider state travels along into {@link #toMessage()}, see
 * {@link ChatMessage.AssistantMessage}.
 */
public record LlmResponse(String text, List<ToolCall> toolCalls, Map<String, Object> providerState) {

	public LlmResponse {
		text = text == null ? "" : text;
		toolCalls = List.copyOf(toolCalls);
		providerState = providerState == null ? Map.of() : providerState;
	}

	public LlmResponse(String text, List<ToolCall> toolCalls) {
		this(text, toolCalls, Map.of());
	}

	public boolean hasToolCalls() {
		return !toolCalls.isEmpty();
	}

	public ChatMessage.AssistantMessage toMessage() {
		return new ChatMessage.AssistantMessage(text, toolCalls, providerState);
	}
}
