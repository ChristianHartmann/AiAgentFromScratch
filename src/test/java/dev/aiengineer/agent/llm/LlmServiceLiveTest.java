package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Talks to the real providers. Every model is skipped unless the key of its provider
 * is present, so the suite stays usable with only one provider configured.
 */
@Tag("llm")
@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class LlmServiceLiveTest {

	@Autowired
	private LlmService service;

	@Autowired
	private ModelRouter router;

	@ParameterizedTest
	@ValueSource(strings = { "google/gemini-3.6-flash", "gpt-5-mini", "anthropic/claude-haiku-4-5" })
	void answersAFactualQuestion(String model) {
		assumeKeyPresentFor(model);

		String answer = service.complete(model, List.of(
			ChatMessage.system("Answer with a single word."),
			ChatMessage.user("What is the capital of France?")));

		assertThat(answer).containsIgnoringCase("Paris");
	}

	@ParameterizedTest
	@ValueSource(strings = { "google/gemini-3.6-flash", "gpt-5-mini", "anthropic/claude-haiku-4-5" })
	void keepsTheConversationAcrossTwoCalls(String model) {
		assumeKeyPresentFor(model);

		Conversation conversation = Conversation.withSystemPrompt("Answer briefly.");
		conversation.addUser("My name is Max.");
		conversation.addAssistant(service.complete(model, conversation.messages()));
		conversation.addUser("What is my name?");

		assertThat(service.complete(model, conversation.messages())).containsIgnoringCase("Max");
	}

	record CityFacts(String city, String country, int populationInMillions) {
	}

	@ParameterizedTest
	@ValueSource(strings = { "google/gemini-3.6-flash", "gpt-5-mini", "anthropic/claude-haiku-4-5" })
	void returnsAStructuredAnswer(String model) {
		assumeKeyPresentFor(model);

		CityFacts facts = service.complete(model,
			List.of(ChatMessage.user("Name the capital of France, its country and its population.")),
			CityFacts.class);

		assertThat(facts.city()).containsIgnoringCase("Paris");
		assertThat(facts.country()).containsIgnoringCase("France");
		assertThat(facts.populationInMillions()).isPositive();
	}

	record Sum(int result) {
	}

	@ParameterizedTest
	@ValueSource(strings = { "google/gemini-3.6-flash", "gpt-5-mini", "anthropic/claude-haiku-4-5" })
	void answersABatchConcurrently(String model) {
		assumeKeyPresentFor(model);
		List<List<ChatMessage>> batch = List.of(
			List.of(ChatMessage.user("What is 2 + 2?")),
			List.of(ChatMessage.user("What is 3 + 3?")),
			List.of(ChatMessage.user("What is 4 + 4?")));

		List<LlmResult<Sum>> results = service.completeAll(model, batch, Sum.class);

		assertThat(results).allMatch(LlmResult::isSuccess);
		assertThat(results).extracting(result -> result.value().result()).containsExactly(4, 6, 8);
	}

	private void assumeKeyPresentFor(String model) {
		String variable = switch (router.provider(model)) {
			case OPENAI -> "OPENAI_API_KEY";
			case ANTHROPIC -> "ANTHROPIC_API_KEY";
			case GOOGLE -> "GEMINI_API_KEY";
		};
		String key = System.getenv(variable);
		assumeTrue(key != null && !key.isBlank(), variable + " is not set");
	}
}
