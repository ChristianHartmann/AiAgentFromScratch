package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;

class LlmClientStructuredTest {

	record ContactDetails(String name, String email) {
	}

	private final ChatModel openAi = mock(ChatModel.class);

	private final LlmClient service = new LlmClient(new ModelRouter(Map.of(Provider.OPENAI, openAi)), ConcurrencyProperties.defaults());

	@Test
	void convertsTheAnswerIntoARecord() {
		answerWith("{\"name\":\"John Smith\",\"email\":\"john@example.com\"}");

		ContactDetails details = service.complete("gpt-5-mini",
			List.of(ChatMessage.user("My name is John Smith, my email is john@example.com.")),
			ContactDetails.class);

		assertThat(details).isEqualTo(new ContactDetails("John Smith", "john@example.com"));
	}

	@Test
	void sendsTheJsonSchemaOfTheRecord() {
		answerWith("{\"name\":\"a\",\"email\":\"b\"}");

		service.complete("gpt-5-mini", List.of(ChatMessage.user("whatever")), ContactDetails.class);

		ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(openAi).call(prompt.capture());
		StructuredOutputChatOptions options = (StructuredOutputChatOptions) prompt.getValue().getOptions();
		assertThat(options.getOutputSchema()).contains("\"name\"").contains("\"email\"");
	}

	private void answerWith(String json) {
		when(openAi.call(any(Prompt.class)))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
	}
}
