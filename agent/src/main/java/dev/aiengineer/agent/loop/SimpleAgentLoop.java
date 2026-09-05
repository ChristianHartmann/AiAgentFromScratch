package dev.aiengineer.agent.loop;

import dev.aiengineer.agent.llm.Conversation;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.LlmRequest;
import dev.aiengineer.agent.llm.LlmResponse;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolChoice;
import dev.aiengineer.agent.llm.ToolResult;
import dev.aiengineer.agent.tool.Toolbox;

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

	public String run(String model, String systemPrompt, String question, Toolbox toolbox) {
		Conversation conversation = Conversation.withSystemPrompt(systemPrompt);
		conversation.addUser(question);
		for (int turn = 1; turn <= maxTurns; turn++) {
			LlmRequest request = new LlmRequest(model);
			request.contents().addAll(conversation.messages());
			request.tools().addAll(toolbox.definitions());
			request.toolChoice(ToolChoice.AUTO);
			LlmResponse response = llm.generate(request);
			if (!response.hasToolCalls()) {
				return response.text();
			}
			response.content().forEach(conversation::add);
			for (ToolCall call : response.toolCalls()) {
				conversation.add(ToolResult.success(call, toolbox.execute(call)));
			}
		}
		throw new IllegalStateException("No final answer after " + maxTurns + " turns");
	}
}
