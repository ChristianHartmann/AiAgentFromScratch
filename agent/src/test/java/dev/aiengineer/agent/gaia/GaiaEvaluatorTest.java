package dev.aiengineer.agent.gaia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.llm.ChatMessage;
import dev.aiengineer.agent.llm.LlmRefusalException;
import dev.aiengineer.agent.llm.LlmResult;
import dev.aiengineer.agent.llm.LlmClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class GaiaEvaluatorTest {

	private static final String GEMINI = "google/gemini-3.6-flash";

	private static final String CLAUDE = "anthropic/claude-haiku-4-5";

	private final GaiaProblem task = new GaiaProblem("t1", "What is the capital of France?", 1, "Paris", "");

	private final LlmClient llm = mock(LlmClient.class);

	private final GaiaEvaluator evaluator = new GaiaEvaluator(llm, new ByteArrayResource("system prompt".getBytes()));

	@Test
	void comparesIgnoringCaseAndSurroundingWhitespace() {
		assertThat(GaiaEvaluator.isCorrect(" Paris ", "paris")).isTrue();
		assertThat(GaiaEvaluator.isCorrect("17", "17")).isTrue();
	}

	@Test
	void countsDifferingAnswersAsWrong() {
		assertThat(GaiaEvaluator.isCorrect("17000", "17")).isFalse();
		assertThat(GaiaEvaluator.isCorrect(null, "17")).isFalse();
		assertThat(GaiaEvaluator.isCorrect("", "17")).isFalse();
	}

	@Test
	void evaluatesEveryTaskAgainstEveryModel() {
		answer(GEMINI, LlmResult.success(new GaiaOutput(true, "", "Paris")));
		answer(CLAUDE, LlmResult.success(new GaiaOutput(false, "Needs a web search", "")));

		List<GaiaResult> results = evaluator.evaluate(List.of(task), List.of(GEMINI, CLAUDE));

		assertThat(results).hasSize(2);
		assertThat(results).filteredOn(GaiaResult::correct).singleElement()
			.satisfies(result -> assertThat(result.model()).isEqualTo(GEMINI));
	}

	@Test
	void keepsTheSelfAssessmentOfTheModel() {
		answer(CLAUDE, LlmResult.success(new GaiaOutput(false, "Needs a web search", "")));

		GaiaResult result = evaluator.evaluate(List.of(task), List.of(CLAUDE)).getFirst();

		assertThat(result.isSolvable()).isFalse();
		assertThat(result.unsolvableReason()).isEqualTo("Needs a web search");
		assertThat(result.failure()).isNull();
	}

	@Test
	void sendsTheSystemPromptFollowedByTheQuestion() {
		answer(GEMINI, LlmResult.success(new GaiaOutput(true, "", "Paris")));

		evaluator.evaluate(List.of(task), List.of(GEMINI));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<List<ChatMessage>>> batch = ArgumentCaptor.forClass(List.class);
		verify(llm).completeAll(eq(GEMINI), batch.capture(), eq(GaiaOutput.class));
		assertThat(batch.getValue()).singleElement().isEqualTo(List.of(
			ChatMessage.system("system prompt"),
			ChatMessage.user("What is the capital of France?")));
	}

	@Test
	void countsARefusalAsUnsolvableInsteadOfAFailure() {
		answer(GEMINI, LlmResult.failure(new LlmRefusalException(GEMINI, "SAFETY")));

		GaiaResult result = evaluator.evaluate(List.of(task), List.of(GEMINI)).getFirst();

		assertThat(result.correct()).isFalse();
		assertThat(result.isSolvable()).isFalse();
		assertThat(result.unsolvableReason()).contains("refused").contains("SAFETY");
		assertThat(result.failure()).isNull();
	}

	@Test
	void recordsAFailedCallAsAResult() {
		answer(GEMINI, LlmResult.failure(new IllegalStateException("Rate limit")));

		GaiaResult result = evaluator.evaluate(List.of(task), List.of(GEMINI)).getFirst();

		assertThat(result.correct()).isFalse();
		assertThat(result.isSolvable()).isNull();
		assertThat(result.failure()).contains("Rate limit");
	}

	@Test
	void promptNamesEveryFieldOfTheResponseSchema() throws IOException {
		String prompt = new ClassPathResource("prompts/gaia-system.txt").getContentAsString(StandardCharsets.UTF_8);
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) new BeanOutputConverter<>(GaiaOutput.class)
			.getJsonSchemaMap().get("properties");

		assertThat(properties.keySet()).isNotEmpty()
			.allSatisfy(field -> assertThat(prompt).contains("\"" + field + "\""));
	}

	private void answer(String model, LlmResult<GaiaOutput> result) {
		when(llm.completeAll(eq(model), any(), eq(GaiaOutput.class))).thenReturn(List.of(result));
	}
}
