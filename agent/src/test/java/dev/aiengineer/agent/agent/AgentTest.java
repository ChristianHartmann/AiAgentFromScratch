package dev.aiengineer.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.context.Event;
import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ContentItem;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.LlmRequest;
import dev.aiengineer.agent.llm.LlmResponse;
import dev.aiengineer.agent.llm.Message;
import dev.aiengineer.agent.llm.Role;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolChoice;
import dev.aiengineer.agent.llm.ToolResult;
import dev.aiengineer.agent.llm.Usage;
import dev.aiengineer.agent.tool.CalculatorTools;
import dev.aiengineer.agent.tool.FunctionTool;
import dev.aiengineer.agent.tool.Tool;
import dev.aiengineer.agent.tool.ToolFunction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentTest {

	private static final String MODEL = "google/gemini-3.6-flash";

	private final LlmClient llm = mock(LlmClient.class);

	private final ToolCall multiply = new ToolCall("call_1", "calculator",
			"{\"operator\":\"MULTIPLY\",\"firstNumber\":1234,\"secondNumber\":5678}");

	private final ToolCall add = new ToolCall("call_2", "calculator",
			"{\"operator\":\"ADD\",\"firstNumber\":1,\"secondNumber\":2}");

	public static class FailingTools {

		@ToolFunction("Always fails.")
		public String broken() {
			throw new IllegalStateException("Service unavailable");
		}

		@ToolFunction("Reads the execution id of the run.")
		public String executionId(ExecutionContext context) {
			return context.executionId();
		}
	}

	@Test
	void returnsTheAnswerWhenNoToolIsNeeded() {
		when(llm.generate(any())).thenReturn(text("Seoul"));

		AgentResult<String> result = agent().run("Capital of South Korea?");

		assertThat(result.status()).isEqualTo(AgentResult.Status.COMPLETE);
		assertThat(result.output()).isEqualTo("Seoul");
		assertThat(result.context().currentStep()).isEqualTo(1);
	}

	@Test
	void runsToolsAndAnswersInTheNextStep() {
		when(llm.generate(any())).thenReturn(calls(multiply)).thenReturn(text("7006652"));

		AgentResult<String> result = agent().run("What is 1234 x 5678?");

		assertThat(result.output()).isEqualTo("7006652");
		assertThat(result.context().currentStep()).isEqualTo(2);
		assertThat(lastRequest().contents()).endsWith(multiply, ToolResult.success(multiply, 7006652.0));
	}

	@Test
	void writesOneEventPerAnswerAndOnePerRoundOfResults() {
		when(llm.generate(any())).thenReturn(calls(multiply)).thenReturn(text("done"));

		List<Event> events = agent().run("What is 1234 x 5678?").context().events();

		assertThat(events).extracting(Event::author).containsExactly("user", "agent", "agent", "agent");
		assertThat(events.get(1).content()).noneMatch(ToolResult.class::isInstance);
		assertThat(events.get(2).content()).allMatch(ToolResult.class::isInstance);
	}

	@Test
	void runsEveryToolCallOfOneAnswerInOrder() {
		when(llm.generate(any())).thenReturn(calls(multiply, add)).thenReturn(text("done"));

		AgentResult<String> result = agent().run("Two calculations");

		assertThat(result.context().events().get(2).content()).containsExactly(
			ToolResult.success(multiply, 7006652.0), ToolResult.success(add, 3.0));
	}

	@Test
	void runsTheSameToolTwiceInOneAnswerWithMatchingIds() {
		ToolCall again = new ToolCall("call_3", "calculator", multiply.arguments());
		when(llm.generate(any())).thenReturn(calls(multiply, again)).thenReturn(text("done"));

		AgentResult<String> result = agent().run("Twice");

		assertThat(result.context().events().get(2).content()).extracting(item -> ((ToolResult) item).toolCallId())
			.containsExactly("call_1", "call_3");
	}

	@Test
	void reportsAnUnknownToolToTheModel() {
		ToolCall teleport = new ToolCall("call_1", "teleport", "{}");
		when(llm.generate(any())).thenReturn(calls(teleport)).thenReturn(text("sorry"));

		AgentResult<String> result = agent().run("Teleport me");

		assertThat(result.context().events().get(2).content())
			.containsExactly(ToolResult.error(teleport, "Tool 'teleport' not found"));
	}

	@Test
	void reportsAFailingToolToTheModelAndGoesOn() {
		ToolCall broken = new ToolCall("call_1", "broken", "{}");
		when(llm.generate(any())).thenReturn(calls(broken)).thenReturn(text("It failed"));

		AgentResult<String> result = agent(FunctionTool.allOf(new FailingTools())).run("Try");

		assertThat(result.status()).isEqualTo(AgentResult.Status.COMPLETE);
		assertThat(result.context().events().get(2).content())
			.containsExactly(ToolResult.error(broken, "Service unavailable"));
	}

	@Test
	void passesTheContextOfTheRunToTheTools() {
		ToolCall readId = new ToolCall("call_1", "executionId", "{}");
		when(llm.generate(any())).thenReturn(calls(readId)).thenReturn(text("done"));

		AgentResult<String> result = agent(FunctionTool.allOf(new FailingTools())).run("Which run?");

		assertThat(result.context().events().get(2).content())
			.containsExactly(ToolResult.success(readId, result.context().executionId()));
	}

	@Test
	void stopsAtTheMaximumNumberOfSteps() {
		when(llm.generate(any())).thenReturn(calls(multiply));

		AgentResult<String> result = Agent.builder(llm).model(MODEL).tools(calculator()).maxSteps(3).build()
			.run("Loop forever");

		assertThat(result.status()).isEqualTo(AgentResult.Status.MAX_STEPS);
		assertThat(result.output()).isNull();
		verify(llm, times(3)).generate(any());
	}

	@Test
	void endsWithAnErrorWhenTheModelFailsAndKeepsTheEventsSoFar() {
		when(llm.generate(any())).thenReturn(calls(multiply)).thenThrow(new IllegalStateException("429 quota"));

		AgentResult<String> result = agent().run("What is 1234 x 5678?");

		assertThat(result.status()).isEqualTo(AgentResult.Status.ERROR);
		assertThat(result.error()).hasMessageContaining("429 quota");
		assertThat(result.context().events()).hasSize(3);
	}

	@Test
	void sendsInstructionsAllEventsAndToolsToTheModel() {
		when(llm.generate(any())).thenReturn(calls(multiply)).thenReturn(text("done"));

		Agent.builder(llm).model(MODEL).instructions("Use the calculator.").tools(calculator()).build()
			.run("What is 1234 x 5678?");

		LlmRequest request = lastRequest();
		assertThat(request.model()).isEqualTo(MODEL);
		assertThat(request.instructions()).containsExactly("Use the calculator.");
		assertThat(request.contents()).startsWith(ContentItem.user("What is 1234 x 5678?"));
		assertThat(request.tools()).extracting(definition -> definition.name()).containsExactly("calculator");
		assertThat(request.toolChoice()).isEqualTo(ToolChoice.AUTO);
	}

	@Test
	void offersNoToolChoiceWithoutTools() {
		when(llm.generate(any())).thenReturn(text("Hi"));

		Agent.builder(llm).model(MODEL).build().run("Hello");

		assertThat(lastRequest().toolChoice()).isNull();
	}

	@Test
	void continuesAnExistingContext() {
		when(llm.generate(any())).thenReturn(text("first")).thenReturn(text("second"));
		Agent<String> agent = agent();
		ExecutionContext context = agent.run("One").context();

		AgentResult<String> result = agent.run("Two", context);

		assertThat(result.output()).isEqualTo("second");
		assertThat(result.context().events()).extracting(Event::author).containsExactly("user", "agent", "user", "agent");
	}

	@Test
	void givesEveryRunItsOwnStepBudget() {
		when(llm.generate(any())).thenReturn(calls(multiply)).thenReturn(calls(multiply)).thenReturn(text("done"));
		Agent<String> agent = Agent.builder(llm).model(MODEL).tools(calculator()).maxSteps(2).build();
		ExecutionContext context = agent.run("Loop").context();

		AgentResult<String> result = agent.run("Now answer", context);

		assertThat(result.status()).isEqualTo(AgentResult.Status.COMPLETE);
		assertThat(result.output()).isEqualTo("done");
	}

	@Test
	void doesNothingWhenTheContextAlreadyHasAResult() {
		ExecutionContext context = new ExecutionContext();
		context.finalResult("known");

		AgentResult<String> result = agent().run(null, context);

		assertThat(result.output()).isEqualTo("known");
		verify(llm, never()).generate(any());
	}

	@Test
	void rejectsTwoToolsWithTheSameName() {
		List<Tool> twice = List.of(calculator().getFirst(), calculator().getFirst());

		assertThatThrownBy(() -> Agent.builder(llm).model(MODEL).tools(twice).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("calculator");
	}

	private Agent<String> agent() {
		return agent(calculator());
	}

	private Agent<String> agent(List<Tool> tools) {
		return Agent.builder(llm).model(MODEL).tools(tools).build();
	}

	private static List<Tool> calculator() {
		return FunctionTool.allOf(new CalculatorTools());
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

	private LlmRequest lastRequest() {
		ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
		verify(llm, atLeastOnce()).generate(request.capture());
		return request.getValue();
	}
}
