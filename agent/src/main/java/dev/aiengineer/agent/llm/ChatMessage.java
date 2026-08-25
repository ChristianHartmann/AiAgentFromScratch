package dev.aiengineer.agent.llm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A message of a conversation. Besides system, user and assistant messages the model sees
 * the results of tools it called, each tied to its call by id.
 */
public sealed interface ChatMessage {

	Role role();

	String content();

	// The factories return the interface on purpose: messages are built to go into lists, and
	// List.of(system(..), user(..)) must stay a List<ChatMessage>.

	static ChatMessage system(String content) {
		return new SystemMessage(content);
	}

	static ChatMessage user(String content) {
		return new UserMessage(content);
	}

	static ChatMessage assistant(String content) {
		return new AssistantMessage(content, List.of());
	}

	static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
		return new AssistantMessage(content, toolCalls);
	}

	static ChatMessage toolResult(ToolCall call, String content) {
		return new ToolResultMessage(call.id(), call.name(), content);
	}

	record SystemMessage(String content) implements ChatMessage {

		public SystemMessage {
			Objects.requireNonNull(content, "content");
		}

		@Override
		public Role role() {
			return Role.SYSTEM;
		}
	}

	record UserMessage(String content) implements ChatMessage {

		public UserMessage {
			Objects.requireNonNull(content, "content");
		}

		@Override
		public Role role() {
			return Role.USER;
		}
	}

	/**
	 * The content is empty when the model only asked for tool calls. The provider state is
	 * opaque to us: data the provider attached to its answer and expects back unchanged in the
	 * next call, such as the thought signatures of Gemini.
	 */
	record AssistantMessage(String content, List<ToolCall> toolCalls, Map<String, Object> providerState)
			implements ChatMessage {

		public AssistantMessage {
			content = content == null ? "" : content;
			toolCalls = List.copyOf(toolCalls);
			providerState = providerState == null ? Map.of()
					: Collections.unmodifiableMap(new LinkedHashMap<>(providerState));
		}

		public AssistantMessage(String content, List<ToolCall> toolCalls) {
			this(content, toolCalls, Map.of());
		}

		@Override
		public Role role() {
			return Role.ASSISTANT;
		}
	}

	record ToolResultMessage(String toolCallId, String toolName, String content) implements ChatMessage {

		public ToolResultMessage {
			Objects.requireNonNull(toolCallId, "toolCallId");
			Objects.requireNonNull(toolName, "toolName");
			Objects.requireNonNull(content, "content");
		}

		@Override
		public Role role() {
			return Role.TOOL;
		}
	}
}
