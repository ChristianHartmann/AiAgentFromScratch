package dev.aiengineer.agent.callback;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolResult;
import dev.aiengineer.agent.tool.Tool;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Approval before tools that require confirmation, section 5.5.3. A denial is an error the
 * model sees; the book's repository reports it as success, so the model may believe a file
 * was deleted.
 */
public class ApprovalCallback implements BeforeToolCallback {

	private final Approver approver;

	private final Map<String, Tool> tools;

	public ApprovalCallback(Approver approver, List<Tool> tools) {
		this.approver = approver;
		this.tools = tools.stream().collect(Collectors.toMap(Tool::name, Function.identity(), (first, second) -> first));
	}

	@Override
	public Optional<ToolResult> beforeTool(ExecutionContext context, ToolCall call) {
		Tool tool = tools.get(call.name());
		if (tool == null || !tool.requiresConfirmation() || approver.approve(call.name(), call.arguments())) {
			return Optional.empty();
		}
		return Optional.of(ToolResult.error(call, "User denied execution of " + call.name()));
	}
}
