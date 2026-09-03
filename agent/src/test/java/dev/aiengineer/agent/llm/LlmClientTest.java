package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class LlmClientTest {

	private final ChatModel openAi = mock(ChatModel.class);

	private final ChatModel google = mock(ChatModel.class);

	private final LlmClient service = new LlmClient(
		new ModelRouter(Map.of(Provider.OPENAI, openAi, Provider.GOOGLE, google)), ConcurrencyProperties.defaults());

	@Test
	void returnsTheTextOfTheAnswer() {
		answerWith(openAi, "Paris");

		String answer = service.complete("gpt-5-mini", List.of(ContentItem.user("What is the capital of France?")));

		assertThat(answer).isEqualTo("Paris");
	}

	@Test
	void translatesRolesIntoSpringAiMessages() {
		answerWith(openAi, "ok");

		service.complete("gpt-5-mini", List.of(
			ContentItem.system("You are helpful."),
			ContentItem.user("Hello"),
			ContentItem.assistant("Hello there"),
			ContentItem.user("How are you?")));

		assertThat(capturedPrompt(openAi).getInstructions()).extracting(Message::getMessageType)
			.containsExactly(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER);
	}

	@Test
	void sendsTheModelNameWithoutProviderPrefix() {
		answerWith(google, "ok");

		service.complete("google/gemini-3.6-flash", List.of(ContentItem.user("Hello")));

		assertThat(capturedPrompt(google).getOptions().getModel()).isEqualTo("gemini-3.6-flash");
	}

	@Test
	void routesTheCallToTheProviderOfTheModel() {
		answerWith(google, "ok");

		service.complete("google/gemini-3.6-flash", List.of(ContentItem.user("Hello")));

		verify(openAi, org.mockito.Mockito.never()).call(any(Prompt.class));
	}

	@Test
	void reportsARefusalWhenTheModelReturnsNoText() {
		when(openAi.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
			new AssistantMessage(""), ChatGenerationMetadata.builder().finishReason("refusal").build()))));

		assertThatThrownBy(() -> service.complete("gpt-5-mini", List.of(ContentItem.user("Hello"))))
			.isInstanceOfSatisfying(LlmRefusalException.class,
				refusal -> assertThat(refusal.finishReason()).isEqualTo("refusal"))
			.hasMessageContaining("gpt-5-mini");
	}

	private static void answerWith(ChatModel chatModel, String text) {
		when(chatModel.call(any(Prompt.class)))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
	}

	private static Prompt capturedPrompt(ChatModel chatModel) {
		ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).call(prompt.capture());
		return prompt.getValue();
	}
}
