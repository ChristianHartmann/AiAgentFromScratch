package dev.aiengineer.agent.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything for one call of the model, the outbound gate of section 4.5.2. Mutable on
 * purpose: from chapter 6 on, hooks adjust instructions and contents before the call.
 */
public final class LlmRequest {

	private final String model;

	private final List<String> instructions = new ArrayList<>();

	private final List<ContentItem> contents = new ArrayList<>();

	private final List<ToolDefinition> tools = new ArrayList<>();

	private ToolChoice toolChoice;

	public LlmRequest(String model) {
		this.model = Objects.requireNonNull(model, "model");
	}

	public String model() {
		return model;
	}

	/** Each instruction becomes its own system message. */
	public List<String> instructions() {
		return instructions;
	}

	public List<ContentItem> contents() {
		return contents;
	}

	public List<ToolDefinition> tools() {
		return tools;
	}

	/** Null when no tools are offered. */
	public ToolChoice toolChoice() {
		return toolChoice;
	}

	public LlmRequest toolChoice(ToolChoice toolChoice) {
		this.toolChoice = toolChoice;
		return this;
	}
}
