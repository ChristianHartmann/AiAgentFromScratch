package dev.aiengineer.agent.llm;

/**
 * Describes a tool to the model: its name, what it does and the JSON schema of its input.
 */
public record ToolDefinition(String name, String description, String inputSchema) {
}
