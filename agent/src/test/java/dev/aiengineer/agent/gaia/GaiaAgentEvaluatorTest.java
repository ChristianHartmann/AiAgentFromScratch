package dev.aiengineer.agent.gaia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.llm.ContentItem;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.LlmRequest;
import dev.aiengineer.agent.llm.LlmResponse;
import dev.aiengineer.agent.llm.Message;
import dev.aiengineer.agent.llm.Role;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.Usage;
import dev.aiengineer.agent.tool.SearxngProperties;
import dev.aiengineer.agent.tool.WebSearchTools;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.RestClient;

class GaiaAgentEvaluatorTest {

	private static final String GEMINI = "google/gemini-3.6-flash";

	private final GaiaProblem task = new GaiaProblem("t1", "Which river?", 1, "Sample River", "",
			"1. A web browser.\n2. A search engine.");

	private final LlmClient llm = mock(LlmClient.class);

	private final GaiaAgentEvaluator evaluator = new GaiaAgentEvaluator(llm,
			new WebSearchTools(RestClient.builder(), new SearxngProperties("http://localhost:8888")),
			new ByteArrayResource("system prompt".getBytes()));

	@Test
	void countsACorrectFinalAnswer() {
		when(llm.generate(any())).thenReturn(finalAnswer("{\"isSolvable\":true,\"unsolvableReason\":\"\",\"finalAnswer\":\"sample river\"}"));

		GaiaResult result = evaluator.evaluate(List.of(task), List.of(GEMINI)).getFirst();

		assertThat(result.correct()).isTrue();
		assertThat(result.isSolvable()).isTrue();
		assertThat(result.failure()).isNull();
	}

	@Test
	void offersTheWebSearchAndTheGaiaPrompt() {
		when(llm.generate(any())).thenReturn(finalAnswer("{\"isSolvable\":true,\"unsolvableReason\":\"\",\"finalAnswer\":\"x\"}"));

		evaluator.evaluate(List.of(task), List.of(GEMINI));

		ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
		verify(llm, atLeastOnce()).generate(request.capture());
		assertThat(request.getValue().instructions()).containsExactly("system prompt");
		assertThat(request.getValue().tools()).extracting(definition -> definition.name())
			.containsExactly("searchWeb", "final_answer");
	}

	@Test
	void countsARunWithoutFinalAnswerAsWrongWithReason() {
		when(llm.generate(any())).thenReturn(new LlmResponse(List.of(new Message(Role.ASSISTANT, "thinking")), Usage.NONE));

		GaiaResult result = evaluator.evaluate(List.of(task), List.of(GEMINI)).getFirst();

		assertThat(result.correct()).isFalse();
		assertThat(result.isSolvable()).isNull();
		assertThat(result.failure()).contains("15");
	}

	@Test
	void countsAFailingRunAsWrongWithTheError() {
		when(llm.generate(any())).thenThrow(new IllegalStateException("429 quota"));

		GaiaResult result = evaluator.evaluate(List.of(task), List.of(GEMINI)).getFirst();

		assertThat(result.correct()).isFalse();
		assertThat(result.failure()).contains("429 quota");
	}

	private static LlmResponse finalAnswer(String output) {
		List<ContentItem> content = new ArrayList<>();
		content.add(new Message(Role.ASSISTANT, ""));
		content.add(new ToolCall("call_1", "final_answer", "{\"output\":" + output + "}"));
		return new LlmResponse(content, Usage.NONE);
	}
}
