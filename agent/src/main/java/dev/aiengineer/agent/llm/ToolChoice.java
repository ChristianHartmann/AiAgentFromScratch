package dev.aiengineer.agent.llm;

/**
 * Whether the model may answer with text (AUTO) or has to call a tool (REQUIRED).
 */
public enum ToolChoice {
	AUTO, REQUIRED
}
