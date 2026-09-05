package dev.aiengineer.agent.llm;

import java.util.List;

/**
 * The answer of a model, the inbound gate of section 4.5.3: an assistant message, possibly
 * followed by the tool calls the caller has to execute.
 */
public record LlmResponse(List<ContentItem> content, Usage usage) {

	public LlmResponse {
		content = List.copyOf(content);
		usage = usage == null ? Usage.NONE : usage;
	}

	public String text() {
		return content.stream()
			.filter(Message.class::isInstance)
			.map(Message.class::cast)
			.filter(message -> message.role() == Role.ASSISTANT)
			.map(Message::content)
			.findFirst()
			.orElse("");
	}

	public List<ToolCall> toolCalls() {
		return content.stream().filter(ToolCall.class::isInstance).map(ToolCall.class::cast).toList();
	}

	public boolean hasToolCalls() {
		return !toolCalls().isEmpty();
	}
}
