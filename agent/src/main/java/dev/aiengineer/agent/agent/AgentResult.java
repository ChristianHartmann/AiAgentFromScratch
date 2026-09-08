package dev.aiengineer.agent.agent;

import dev.aiengineer.agent.context.ExecutionContext;

/**
 * Outcome of a run with the context for inspection. Unlike the book in chapter 4, a status
 * tells complete runs from runs that hit the step limit or failed; chapter 6 adds more.
 */
public record AgentResult<T>(Status status, T output, ExecutionContext context, String error) {

	public enum Status {
		COMPLETE, MAX_STEPS, ERROR
	}
}
