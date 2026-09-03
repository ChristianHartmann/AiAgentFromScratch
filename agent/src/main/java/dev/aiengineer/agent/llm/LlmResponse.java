package dev.aiengineer.agent.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Answer of a model that may call tools: either text, or tool calls the caller has to run.
 * The provider state travels along into {@link #toContents()}, see {@link Message}.
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

	/**
	 * The answer as content items for the history: an assistant message carrying text and
	 * provider state, followed by the tool calls.
	 */
	public List<ContentItem> toContents() {
		List<ContentItem> contents = new ArrayList<>();
		contents.add(new Message(Role.ASSISTANT, text, providerState));
		contents.addAll(toolCalls);
		return contents;
	}
}
