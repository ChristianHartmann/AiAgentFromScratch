package dev.aiengineer.agent.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.llm.ContentItem;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.LlmRequest;
import dev.aiengineer.agent.llm.LlmResponse;
import dev.aiengineer.agent.llm.Message;
import dev.aiengineer.agent.llm.Role;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolResult;
import dev.aiengineer.agent.llm.Usage;
import dev.aiengineer.agent.tool.CalculatorTools;
import dev.aiengineer.agent.tool.Toolbox;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SimpleAgentLoopTest {

	private static final String MODEL = "google/gemini-3.6-flash";

	private final LlmClient llm = mock(LlmClient.class);

	private final Toolbox toolbox = Toolbox.of(new CalculatorTools());

	private final ToolCall multiply = new ToolCall("call_1", "calculator",
			"{\"operator\":\"MULTIPLY\",\"firstNumber\":1234,\"secondNumber\":5678}");

	private final ToolCall add = new ToolCall("call_2", "calculator",
			"{\"operator\":\"ADD\",\"firstNumber\":1,\"secondNumber\":2}");

	@Test
	void returnsTheAnswerWhenNoToolIsNeeded() {
		when(llm.generate(any(LlmRequest.class))).thenReturn(text("Seoul"));

		assertThat(loop().run(MODEL, "system", "Capital of South Korea?", toolbox)).isEqualTo("Seoul");
	}

	@Test
	void offersTheToolsOfTheToolbox() {
		when(llm.generate(any(LlmRequest.class))).thenReturn(text("Seoul"));

		loop().run(MODEL, "system", "Capital?", toolbox);

		assertThat(requestOfCall(1).model()).isEqualTo(MODEL);
		assertThat(requestOfCall(1).tools()).isEqualTo(toolbox.definitions());
	}

	@Test
	void runsTheToolAndSendsTheResultBack() {
		when(llm.generate(any(LlmRequest.class)))
			.thenReturn(toolCalls(multiply))
			.thenReturn(text("1234 x 5678 = 7006652"));

		String answer = loop().run(MODEL, "system", "What is 1234 x 5678?", toolbox);

		assertThat(answer).isEqualTo("1234 x 5678 = 7006652");
		assertThat(historyOfCall(2)).containsExactly(
			ContentItem.system("system"),
			ContentItem.user("What is 1234 x 5678?"),
			new Message(Role.ASSISTANT, ""),
			multiply,
			ToolResult.success(multiply, "7006652.0"));
	}

	@Test
	void runsEveryToolCallOfOneAnswerInOrder() {
		when(llm.generate(any(LlmRequest.class)))
			.thenReturn(toolCalls(multiply, add))
			.thenReturn(text("done"));

		loop().run(MODEL, "system", "Two calculations", toolbox);

		assertThat(historyOfCall(2)).endsWith(
			ToolResult.success(multiply, "7006652.0"),
			ToolResult.success(add, "3.0"));
	}

	@Test
	void everyCallSeesTheHistoryAsItWasAtThatMoment() {
		when(llm.generate(any(LlmRequest.class)))
			.thenReturn(toolCalls(multiply))
			.thenReturn(text("done"));

		loop().run(MODEL, "system", "What is 1234 x 5678?", toolbox);

		assertThat(historyOfCall(1)).hasSize(2);
	}

	@Test
	void givesUpAfterTheMaximumNumberOfTurns() {
		when(llm.generate(any(LlmRequest.class))).thenReturn(toolCalls(multiply));

		assertThatThrownBy(() -> new SimpleAgentLoop(llm, 3).run(MODEL, "system", "Loop forever", toolbox))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("3");
		verify(llm, times(3)).generate(any(LlmRequest.class));
	}

	private SimpleAgentLoop loop() {
		return new SimpleAgentLoop(llm);
	}

	private static LlmResponse text(String text) {
		return new LlmResponse(List.of(ContentItem.assistant(text)), Usage.NONE);
	}

	private static LlmResponse toolCalls(ToolCall... calls) {
		List<ContentItem> content = new ArrayList<>();
		content.add(new Message(Role.ASSISTANT, ""));
		content.addAll(List.of(calls));
		return new LlmResponse(content, Usage.NONE);
	}

	private List<ContentItem> historyOfCall(int number) {
		return requestOfCall(number).contents();
	}

	private LlmRequest requestOfCall(int number) {
		ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
		verify(llm, atLeast(number)).generate(request.capture());
		return request.getAllValues().get(number - 1);
	}
}
