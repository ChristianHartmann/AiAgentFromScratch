package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class ModelRouterTest {

	private final ChatModel openAi = mock(ChatModel.class);

	private final ChatModel anthropic = mock(ChatModel.class);

	private final ChatModel google = mock(ChatModel.class);

	private final ModelRouter router = new ModelRouter(Map.of(
		Provider.OPENAI, openAi,
		Provider.ANTHROPIC, anthropic,
		Provider.GOOGLE, google));

	@Test
	void selectsAnthropicByPrefix() {
		assertThat(router.provider("anthropic/claude-haiku-4-5")).isEqualTo(Provider.ANTHROPIC);
		assertThat(router.chatModel("anthropic/claude-haiku-4-5")).isSameAs(anthropic);
	}

	@Test
	void selectsGoogleByPrefix() {
		assertThat(router.provider("google/gemini-2.5-flash")).isEqualTo(Provider.GOOGLE);
		assertThat(router.chatModel("google/gemini-2.5-flash")).isSameAs(google);
	}

	@Test
	void selectsOpenAiWithoutPrefix() {
		assertThat(router.provider("gpt-5-mini")).isEqualTo(Provider.OPENAI);
		assertThat(router.chatModel("gpt-5-mini")).isSameAs(openAi);
	}

	@Test
	void stripsThePrefixFromTheModelName() {
		assertThat(router.modelId("anthropic/claude-haiku-4-5")).isEqualTo("claude-haiku-4-5");
		assertThat(router.modelId("google/gemini-2.5-flash")).isEqualTo("gemini-2.5-flash");
		assertThat(router.modelId("gpt-5-mini")).isEqualTo("gpt-5-mini");
	}

	@Test
	void reportsUnknownPrefixWithAClearMessage() {
		assertThatThrownBy(() -> router.provider("mistral/mistral-large"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("mistral");
	}
}
