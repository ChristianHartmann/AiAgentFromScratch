package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

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
class LlmClientLiveTest {

	@Autowired
	private LlmClient service;

	@Autowired
	private ModelRouter router;

	@ParameterizedTest
	@ValueSource(strings = { "google/gemini-3.6-flash", "gpt-5-mini", "anthropic/claude-haiku-4-5" })
	void keepsTheConversationAcrossTwoCalls(String model) {
		LiveTests.assumeKeyPresentFor(router, model);

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
		LiveTests.assumeKeyPresentFor(router, model);

		CityFacts facts = service.complete(model,
			List.of(ContentItem.user("Name the capital of France, its country and its population.")),
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
		LiveTests.assumeKeyPresentFor(router, model);
		List<List<ContentItem>> batch = List.of(
			List.of(ContentItem.user("What is 2 + 2?")),
			List.of(ContentItem.user("What is 3 + 3?")));

		List<LlmResult<Sum>> results = service.completeAll(model, batch, Sum.class);

		assertThat(results).allMatch(LlmResult::isSuccess);
		assertThat(results).extracting(result -> result.value().result()).containsExactly(4, 6);
	}
}
