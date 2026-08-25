package dev.aiengineer.agent.llm;

/**
 * A call of a tool as requested by the model. The arguments are JSON as the model sent it.
 */
public record ToolCall(String id, String name, String arguments) {
}
