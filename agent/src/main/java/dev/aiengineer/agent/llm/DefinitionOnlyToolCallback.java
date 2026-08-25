package dev.aiengineer.agent.llm;

import org.springframework.ai.tool.ToolCallback;

/**
 * Hands a tool definition to Spring AI without an implementation. Spring AI requires a
 * callback, but tools are run by our own loop: the chat models of all three providers only
 * resolve the definitions and never execute a callback themselves.
 */
record DefinitionOnlyToolCallback(ToolDefinition definition) implements ToolCallback {

	@Override
	public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
		return org.springframework.ai.tool.definition.ToolDefinition.builder()
			.name(definition.name())
			.description(definition.description())
			.inputSchema(definition.inputSchema())
			.build();
	}

	@Override
	public String call(String toolInput) {
		throw new UnsupportedOperationException(
				"Tools are executed by our own loop, not by Spring AI: " + definition.name());
	}
}
