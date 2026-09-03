package dev.aiengineer.agent.llm;

import java.util.Objects;

/**
 * The outcome of a tool call, tied to the call by its id. The content is whatever the tool
 * returned, a typed record for final_answer; it becomes text only when sent to the model.
 */
public record ToolResult(String toolCallId, String name, Status status, Object content) implements ContentItem {

	public enum Status {
		SUCCESS, ERROR
	}

	public ToolResult {
		Objects.requireNonNull(toolCallId, "toolCallId");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(status, "status");
	}

	public static ToolResult success(ToolCall call, Object content) {
		return new ToolResult(call.id(), call.name(), Status.SUCCESS, content);
	}

	public static ToolResult error(ToolCall call, String message) {
		return new ToolResult(call.id(), call.name(), Status.ERROR, message);
	}
}
