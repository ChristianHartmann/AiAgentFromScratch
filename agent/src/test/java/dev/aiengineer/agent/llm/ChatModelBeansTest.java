package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.ai.openai.api-key=test-key",
	"spring.ai.anthropic.api-key=test-key",
	"spring.ai.google.genai.api-key=test-key"
})
class ChatModelBeansTest {

	@Autowired
	private ModelRouter router;

	@Test
	void knowsAllThreeProviders() {
		assertThat(router.chatModel("gpt-5-mini")).isNotNull();
		assertThat(router.chatModel("anthropic/claude-haiku-4-5")).isNotNull();
		assertThat(router.chatModel("google/gemini-3.6-flash")).isNotNull();
	}
}
