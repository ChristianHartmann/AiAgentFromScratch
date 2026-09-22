package dev.aiengineer.agent.callback;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolResult;
import java.util.Optional;

/**
 * Runs after a tool, section 5.5.2, and may replace its result. Unlike the book it also gets
 * the call, so a callback reads the arguments directly instead of searching the events for
 * the call with the same id (Listing 5.30).
 */
@FunctionalInterface
public interface AfterToolCallback {

	Optional<ToolResult> afterTool(ExecutionContext context, ToolCall call, ToolResult result);
}
