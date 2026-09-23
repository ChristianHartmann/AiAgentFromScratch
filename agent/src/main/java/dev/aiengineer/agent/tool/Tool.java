package dev.aiengineer.agent.tool;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ToolDefinition;

/**
 * The unified tool interface of section 4.4.2. Every tool gets the context of the run, tools
 * without need ignore it. A failing tool throws; the agent turns that into an error result.
 */
public interface Tool {

	ToolDefinition definition();

	Object execute(ExecutionContext context, String arguments) throws Exception;

	default String name() {
		return definition().name();
	}

	default String description() {
		return definition().description();
	}

	default boolean requiresConfirmation() {
		return false;
	}
}
