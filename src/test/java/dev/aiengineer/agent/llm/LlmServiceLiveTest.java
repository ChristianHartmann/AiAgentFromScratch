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
