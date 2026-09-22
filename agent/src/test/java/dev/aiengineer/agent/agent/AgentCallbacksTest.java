package dev.aiengineer.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.callback.AfterToolCallback;
import dev.aiengineer.agent.callback.BeforeToolCallback;
import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ContentItem;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.LlmResponse;
import dev.aiengineer.agent.llm.Message;
import dev.aiengineer.agent.llm.Role;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolDefinition;
import dev.aiengineer.agent.llm.ToolResult;
import dev.aiengineer.agent.llm.Usage;
import dev.aiengineer.agent.tool.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentCallbacksTest {

	private static final String MODEL = "google/gemini-3.6-flash";

	private final LlmClient llm = mock(LlmClient.class);

	private final AtomicInteger executions = new AtomicInteger();

	private final Tool counter = new Tool() {

		@Override
		public ToolDefinition definition() {
			return new ToolDefinition("count", "Counts its executions", "{\"type\":\"object\"}");
		}

		@Override
		public Object execute(ExecutionContext context, String arguments) {
			return "executed " + executions.incrementAndGet();
		}
	};

	private final ToolCall call = new ToolCall("call_1", "count", "{}");

	@Test
	void runsTheToolWhenNoCallbackSteps() {
		List<ToolResult> results = run(List.of((context, toolCall) -> Optional.empty()), List.of());

		assertThat(results).containsExactly(ToolResult.success(call, "executed 1"));
	}

	@Test
	void usesTheResultOfABeforeCallbackInsteadOfTheTool() {
		List<ToolResult> results = run(List.of((context, toolCall) -> Optional.of(ToolResult.success(toolCall, "cached"))),
				List.of());

		assertThat(results).containsExactly(ToolResult.success(call, "cached"));
		assertThat(executions).hasValue(0);
	}

	@Test
	void stopsAtTheFirstBeforeCallbackWithAResult() {
		AtomicInteger secondCalls = new AtomicInteger();
		List<ToolResult> results = run(List.of(
				(context, toolCall) -> Optional.of(ToolResult.success(toolCall, "first")),
				(context, toolCall) -> {
					secondCalls.incrementAndGet();
					return Optional.of(ToolResult.success(toolCall, "second"));
				}), List.of());

		assertThat(results).containsExactly(ToolResult.success(call, "first"));
		assertThat(secondCalls).hasValue(0);
	}

	@Test
	void replacesTheResultWithTheFirstAfterCallbackThatReturnsOne() {
		List<ToolResult> results = run(List.of(), List.of(
				(context, toolCall, result) -> Optional.empty(),
				(context, toolCall, result) -> Optional.of(ToolResult.success(toolCall, "shortened")),
				(context, toolCall, result) -> Optional.of(ToolResult.success(toolCall, "never"))));

		assertThat(results).containsExactly(ToolResult.success(call, "shortened"));
	}

	@Test
	void runsAfterCallbacksAlsoOnTheResultOfABeforeCallback() {
		List<ToolResult> results = run(
				List.of((context, toolCall) -> Optional.of(ToolResult.success(toolCall, "cached"))),
				List.of((context, toolCall, result) -> Optional.of(
						ToolResult.success(toolCall, result.content() + ", checked"))));

		assertThat(results).containsExactly(ToolResult.success(call, "cached, checked"));
	}

	@Test
	void keepsTheToolFromRunningWhenABeforeCallbackFails() {
		List<ToolResult> results = run(List.of((context, toolCall) -> {
			throw new IllegalStateException("approval service down");
		}), List.of());

		assertThat(results).singleElement().satisfies(result -> {
			assertThat(result.status()).isEqualTo(ToolResult.Status.ERROR);
			assertThat((String) result.content()).contains("approval service down");
		});
		assertThat(executions).hasValue(0);
	}

	@Test
	void turnsAFailingAfterCallbackIntoAnError() {
		List<ToolResult> results = run(List.of(), List.of((context, toolCall, result) -> {
			throw new IllegalStateException("embedding failed");
		}));

		assertThat(results).singleElement()
			.satisfies(result -> assertThat(result.status()).isEqualTo(ToolResult.Status.ERROR));
	}

	@Test
	void rejectsACallbackResultThatBelongsToAnotherCall() {
		ToolCall other = new ToolCall("call_9", "count", "{}");
		List<ToolResult> results = run(List.of((context, toolCall) -> Optional.of(ToolResult.success(other, "x"))),
				List.of());

		assertThat(results).singleElement().satisfies(result -> {
			assertThat(result.toolCallId()).isEqualTo("call_1");
			assertThat(result.status()).isEqualTo(ToolResult.Status.ERROR);
		});
	}

	@Test
	void givesCallbacksTheContextAndTheCall() {
		List<String> seen = new ArrayList<>();
		BeforeToolCallback before = (context, toolCall) -> {
			seen.add(context.executionId() + " " + toolCall.id());
			return Optional.empty();
		};

		ExecutionContext context = new ExecutionContext();
		agent(List.of(before), List.of()).act(context, List.of(call));

		assertThat(seen).containsExactly(context.executionId() + " call_1");
	}

	@Test
	void keepsTheCallbacksWhenTheOutputTypeIsSetAfterwards() {
		record Answer(String value) {
		}
		Agent<Answer> typed = Agent.builder(llm)
			.model(MODEL)
			.tools(List.of(counter))
			.beforeToolCallbacks(List.of((context, toolCall) -> Optional.of(ToolResult.success(toolCall, "cached"))))
			.outputType(Answer.class)
			.build();

		assertThat(typed.act(new ExecutionContext(), List.of(call))).containsExactly(ToolResult.success(call, "cached"));
	}

	@Test
	void callsTheCallbacksDuringARun() {
		when(llm.generate(any())).thenReturn(calls(call)).thenReturn(text("done"));

		AgentResult<String> result = agent(
				List.of((context, toolCall) -> Optional.of(ToolResult.success(toolCall, "cached"))), List.of())
			.run("Count");

		assertThat(result.context().events().get(2).content()).containsExactly(ToolResult.success(call, "cached"));
	}

	private List<ToolResult> run(List<BeforeToolCallback> before, List<AfterToolCallback> after) {
		return agent(before, after).act(new ExecutionContext(), List.of(call));
	}

	private Agent<String> agent(List<BeforeToolCallback> before, List<AfterToolCallback> after) {
		return Agent.builder(llm)
			.model(MODEL)
			.tools(List.of(counter))
			.beforeToolCallbacks(before)
			.afterToolCallbacks(after)
			.build();
	}

	private static LlmResponse text(String text) {
		return new LlmResponse(List.of(new Message(Role.ASSISTANT, text)), Usage.NONE);
	}

	private static LlmResponse calls(ToolCall... calls) {
		List<ContentItem> content = new ArrayList<>();
		content.add(new Message(Role.ASSISTANT, ""));
		content.addAll(List.of(calls));
		return new LlmResponse(content, Usage.NONE);
	}
}
