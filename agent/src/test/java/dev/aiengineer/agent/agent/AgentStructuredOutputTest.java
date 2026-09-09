package dev.aiengineer.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.agent.FinalAnswerToolTest.Mood;
import dev.aiengineer.agent.agent.FinalAnswerToolTest.Sentiment;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentStructuredOutputTest {

	private static final String MODEL = "google/gemini-3.6-flash";

	private static final String VALID = "{\"output\":{\"sentiment\":\"POSITIVE\",\"confidence\":0.9,\"keyPhrases\":[\"great\"]}}";

	private final LlmClient llm = mock(LlmClient.class);

	private final Agent<Sentiment> agent = Agent.builder(llm).model(MODEL).outputType(Sentiment.class).maxSteps(4).build();

	@Test
	void forcesAToolCallAndOffersFinalAnswer() {
		when(llm.generate(any())).thenReturn(calls(new ToolCall("call_1", "final_answer", VALID)));

		agent.run("I love it");

		LlmRequest request = lastRequest();
		assertThat(request.toolChoice()).isEqualTo(ToolChoice.REQUIRED);
		assertThat(request.tools()).extracting(definition -> definition.name()).containsExactly("final_answer");
	}

	@Test
	void endsWithTheTypedOutputOfFinalAnswer() {
		when(llm.generate(any())).thenReturn(calls(new ToolCall("call_1", "final_answer", VALID)));

		AgentResult<Sentiment> result = agent.run("I love it");

		assertThat(result.status()).isEqualTo(AgentResult.Status.COMPLETE);
		assertThat(result.output()).isEqualTo(new Sentiment(Mood.POSITIVE, 0.9, List.of("great")));
	}

	@Test
	void letsTheModelCorrectAnAnswerThatDoesNotMatchTheType() {
		ToolCall wrong = new ToolCall("call_1", "final_answer", "{\"output\":{\"sentiment\":\"furious\"}}");
		when(llm.generate(any())).thenReturn(calls(wrong)).thenReturn(calls(new ToolCall("call_2", "final_answer", VALID)));

		AgentResult<Sentiment> result = agent.run("I love it");

		assertThat(result.output().sentiment()).isEqualTo(Mood.POSITIVE);
		assertThat(result.context().events().get(2).content()).singleElement()
			.satisfies(item -> assertThat(((ToolResult) item).status()).isEqualTo(ToolResult.Status.ERROR));
	}

	@Test
	void runsOtherToolsCalledTogetherWithFinalAnswerAndEnds() {
		Agent<Sentiment> withCalculator = Agent.builder(llm).model(MODEL).outputType(Sentiment.class)
			.tools(FunctionTool.allOf(new CalculatorTools())).build();
		ToolCall add = new ToolCall("call_1", "calculator", "{\"operator\":\"ADD\",\"firstNumber\":1,\"secondNumber\":2}");
		when(llm.generate(any())).thenReturn(calls(add, new ToolCall("call_2", "final_answer", VALID)));

		AgentResult<Sentiment> result = withCalculator.run("I love it");

		assertThat(result.status()).isEqualTo(AgentResult.Status.COMPLETE);
		assertThat(result.context().events().get(2).content()).hasSize(2);
	}

	@Test
	void keepsGoingWhenTheModelAnswersWithTextDespiteTheForcedToolCall() {
		when(llm.generate(any())).thenReturn(new LlmResponse(List.of(new Message(Role.ASSISTANT, "It is positive")), Usage.NONE));

		AgentResult<Sentiment> result = agent.run("I love it");

		assertThat(result.status()).isEqualTo(AgentResult.Status.MAX_STEPS);
		assertThat(result.context().currentStep()).isEqualTo(4);
	}

	@Test
	void reservesTheNameFinalAnswer() {
		List<Tool> clash = List.of(new FinalAnswerTool<>(Sentiment.class));

		assertThatThrownBy(() -> Agent.builder(llm).model(MODEL).tools(clash).outputType(Sentiment.class).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("final_answer");
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
