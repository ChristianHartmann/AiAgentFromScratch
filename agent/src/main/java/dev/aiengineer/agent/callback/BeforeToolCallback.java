package dev.aiengineer.agent.callback;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolResult;
import java.util.Optional;

/**
 * Runs before a tool, section 5.5.2. A result replaces the tool: it does not run, and the
 * result goes to the model. The book returns any value; a ToolResult lets a callback say
 * that it refused, with status ERROR.
 */
@FunctionalInterface
public interface BeforeToolCallback {

	Optional<ToolResult> beforeTool(ExecutionContext context, ToolCall call);
}
