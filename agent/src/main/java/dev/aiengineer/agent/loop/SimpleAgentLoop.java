package dev.aiengineer.agent.loop;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.Conversation;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.LlmRequest;
import dev.aiengineer.agent.llm.LlmResponse;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolChoice;
import dev.aiengineer.agent.llm.ToolResult;
import dev.aiengineer.agent.tool.Tool;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The agent loop of section 3.3.3: ask the model, run the tools it calls, feed the results
 * back, and repeat until it answers. Unlike {@code while True} in the book, the loop gives up
 * after a number of turns, so a model that keeps calling tools cannot drain the quota.
 */
public class SimpleAgentLoop {

	public static final int DEFAULT_MAX_TURNS = 10;

	private final LlmClient llm;

	private final int maxTurns;

	public SimpleAgentLoop(LlmClient llm) {
		this(llm, DEFAULT_MAX_TURNS);
	}

	public SimpleAgentLoop(LlmClient llm, int maxTurns) {
		this.llm = llm;
		this.maxTurns = maxTurns;
	}

	public String run(String model, String systemPrompt, String question, List<Tool> tools) {
		Map<String, Tool> toolsByName = tools.stream().collect(Collectors.toMap(Tool::name, tool -> tool));
		ExecutionContext context = new ExecutionContext();
		Conversation conversation = Conversation.withSystemPrompt(systemPrompt);
		conversation.addUser(question);
		for (int turn = 1; turn <= maxTurns; turn++) {
			LlmRequest request = new LlmRequest(model);
			request.contents().addAll(conversation.messages());
			request.tools().addAll(tools.stream().map(Tool::definition).toList());
			request.toolChoice(ToolChoice.AUTO);
			LlmResponse response = llm.generate(request);
			if (!response.hasToolCalls()) {
				return response.text();
			}
			response.content().forEach(conversation::add);
			for (ToolCall call : response.toolCalls()) {
				conversation.add(execute(toolsByName, context, call));
			}
		}
		throw new IllegalStateException("No final answer after " + maxTurns + " turns");
	}

	private static ToolResult execute(Map<String, Tool> tools, ExecutionContext context, ToolCall call) {
		Tool tool = tools.get(call.name());
		if (tool == null) {
			return ToolResult.error(call, "Tool '" + call.name() + "' not found");
		}
		try {
			return ToolResult.success(call, tool.execute(context, call.arguments()));
		}
		catch (Exception ex) {
			return ToolResult.error(call, ex.getMessage());
		}
	}
}
