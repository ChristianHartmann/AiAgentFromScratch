package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The five steps of tool calling from section 3.2.1, with the tool definition written by
 * hand exactly as in the book.
 */
@Tag("llm")
@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class ToolCallingLiveTest {

	private static final String MODEL = "google/gemini-3.6-flash";

	private static final ToolDefinition CALCULATOR = new ToolDefinition("calculator",
			"Perform basic arithmetic operations.", """
					{
					  "type": "object",
					  "properties": {
					    "operator": {"type": "string", "description": "Arithmetic operation to perform",
					                 "enum": ["add", "subtract", "multiply", "divide"]},
					    "first_number": {"type": "number", "description": "First number for the calculation"},
					    "second_number": {"type": "number", "description": "Second number for the calculation"}
					  },
					  "required": ["operator", "first_number", "second_number"]
					}
					""");

	@Autowired
	private LlmClient service;

	@Autowired
	private ModelRouter router;

	@Test
	void answersWithoutToolWhenNoCalculationIsNeeded() {
		LiveTests.assumeKeyPresentFor(router, MODEL);

		LlmResponse response = service.respond(MODEL,
			List.of(ChatMessage.user("What is the capital of South Korea?")), List.of(CALCULATOR));

		assertThat(response.hasToolCalls()).isFalse();
		assertThat(response.text()).containsIgnoringCase("Seoul");
	}

	@Test
	void callsTheCalculatorAndUsesItsResult() {
		LiveTests.assumeKeyPresentFor(router, MODEL);
		Conversation conversation = new Conversation();
		conversation.addUser("What is 1234 x 5678?");

		LlmResponse first = service.respond(MODEL, conversation.messages(), List.of(CALCULATOR));

		assertThat(first.toolCalls()).singleElement().satisfies(call -> {
			assertThat(call.id()).as("every tool call carries an id to tie its result to it").isNotBlank();
			assertThat(call.name()).isEqualTo("calculator");
			assertThat(call.arguments()).contains("multiply").contains("1234").contains("5678");
		});
		conversation.add(first.toMessage());
		conversation.add(ChatMessage.toolResult(first.toolCalls().getFirst(), "7006652"));

		LlmResponse second = service.respond(MODEL, conversation.messages(), List.of(CALCULATOR));

		assertThat(second.hasToolCalls()).isFalse();
		assertThat(second.text().replaceAll("[,.\\s]", "")).contains("7006652");
	}
}
