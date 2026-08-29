package dev.aiengineer.agent.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.llm.ChatMessage;
import dev.aiengineer.agent.llm.LlmResponse;
import dev.aiengineer.agent.llm.LlmService;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.tool.CalculatorTools;
import dev.aiengineer.agent.tool.Toolbox;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SimpleAgentLoopTest {

	private static final String MODEL = "google/gemini-3.6-flash";

	private final LlmService llm = mock(LlmService.class);

	private final Toolbox toolbox = Toolbox.of(new CalculatorTools());

	private final ToolCall multiply = new ToolCall("call_1", "calculator",
			"{\"operator\":\"MULTIPLY\",\"firstNumber\":1234,\"secondNumber\":5678}");

	private final ToolCall add = new ToolCall("call_2", "calculator",
			"{\"operator\":\"ADD\",\"firstNumber\":1,\"secondNumber\":2}");

	@Test
	void returnsTheAnswerWhenNoToolIsNeeded() {
		when(llm.respond(eq(MODEL), anyList(), anyList())).thenReturn(text("Seoul"));

		assertThat(loop().run(MODEL, "system", "Capital of South Korea?", toolbox)).isEqualTo("Seoul");
	}

	@Test
	void offersTheToolsOfTheToolbox() {
		when(llm.respond(eq(MODEL), anyList(), anyList())).thenReturn(text("Seoul"));

		loop().run(MODEL, "system", "Capital?", toolbox);

		verify(llm).respond(eq(MODEL), anyList(), eq(toolbox.definitions()));
	}

	@Test
	void runsTheToolAndSendsTheResultBack() {
		when(llm.respond(eq(MODEL), anyList(), anyList()))
			.thenReturn(new LlmResponse("", List.of(multiply)))
			.thenReturn(text("1234 x 5678 = 7006652"));

		String answer = loop().run(MODEL, "system", "What is 1234 x 5678?", toolbox);

		assertThat(answer).isEqualTo("1234 x 5678 = 7006652");
		assertThat(historyOfCall(2)).containsExactly(
			ChatMessage.system("system"),
			ChatMessage.user("What is 1234 x 5678?"),
			ChatMessage.assistant("", List.of(multiply)),
			ChatMessage.toolResult(multiply, "7006652.0"));
	}

	@Test
	void runsEveryToolCallOfOneAnswerInOrder() {
		when(llm.respond(eq(MODEL), anyList(), anyList()))
			.thenReturn(new LlmResponse("", List.of(multiply, add)))
			.thenReturn(text("done"));

		loop().run(MODEL, "system", "Two calculations", toolbox);

		assertThat(historyOfCall(2)).endsWith(
			ChatMessage.toolResult(multiply, "7006652.0"),
			ChatMessage.toolResult(add, "3.0"));
	}

	@Test
	void everyCallSeesTheHistoryAsItWasAtThatMoment() {
		when(llm.respond(eq(MODEL), anyList(), anyList()))
			.thenReturn(new LlmResponse("", List.of(multiply)))
			.thenReturn(text("done"));

		loop().run(MODEL, "system", "What is 1234 x 5678?", toolbox);

		assertThat(historyOfCall(1)).hasSize(2);
	}

	@Test
	void givesUpAfterTheMaximumNumberOfTurns() {
		when(llm.respond(eq(MODEL), anyList(), anyList())).thenReturn(new LlmResponse("", List.of(multiply)));

		assertThatThrownBy(() -> new SimpleAgentLoop(llm, 3).run(MODEL, "system", "Loop forever", toolbox))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("3");
		verify(llm, times(3)).respond(any(), anyList(), anyList());
	}

	private SimpleAgentLoop loop() {
		return new SimpleAgentLoop(llm);
	}

	private static LlmResponse text(String text) {
		return new LlmResponse(text, List.of());
	}

	@SuppressWarnings("unchecked")
	private List<ChatMessage> historyOfCall(int number) {
		ArgumentCaptor<List<ChatMessage>> history = ArgumentCaptor.forClass(List.class);
		verify(llm, atLeast(number)).respond(eq(MODEL), history.capture(), anyList());
		return history.getAllValues().get(number - 1);
	}
}
