package dev.aiengineer.agent.llm;

/**
 * Tokens of one call, kept for the evaluation in chapter 10.
 */
public record Usage(int inputTokens, int outputTokens) {

	public static final Usage NONE = new Usage(0, 0);
}
