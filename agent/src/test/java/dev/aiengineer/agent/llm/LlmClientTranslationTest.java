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
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class LlmClientTranslationTest {

	record Weather(String city, int temperature) {
	}

	private final ToolDefinition search = new ToolDefinition("searchWeb", "Search the web", "{\"type\":\"object\"}");

	private final ToolCall first = new ToolCall("call_1", "searchWeb", "{\"query\":\"a\"}");

	private final ToolCall second = new ToolCall("call_2", "searchWeb", "{\"query\":\"b\"}");

	private final ChatModel openAi = mock(ChatModel.class);

	private final LlmClient client = new LlmClient(new ModelRouter(Map.of(Provider.OPENAI, openAi)),
		ConcurrencyProperties.defaults());

	@Test
	void mergesAssistantTextAndTheFollowingToolCallsIntoOneMessage() {
		List<org.springframework.ai.chat.messages.Message> sent = send(List.of(
			ContentItem.user("Search"),
			new Message(Role.ASSISTANT, "Let me search", Map.of("thoughtSignatures", List.of("sig"))),
			first, second));

		assertThat(sent).extracting(org.springframework.ai.chat.messages.Message::getMessageType)
			.containsExactly(MessageType.USER, MessageType.ASSISTANT);
		AssistantMessage assistant = (AssistantMessage) sent.get(1);
		assertThat(assistant.getText()).isEqualTo("Let me search");
		assertThat(assistant.getMetadata()).containsEntry("thoughtSignatures", List.of("sig"));
		assertThat(assistant.getToolCalls()).extracting(AssistantMessage.ToolCall::id).containsExactly("call_1", "call_2");
	}

	@Test
	void createsAnAssistantMessageForToolCallsWithoutText() {
		List<org.springframework.ai.chat.messages.Message> sent = send(List.of(ContentItem.user("Search"), first));

		assertThat(((AssistantMessage) sent.get(1)).getToolCalls()).singleElement()
			.satisfies(call -> assertThat(call.id()).isEqualTo("call_1"));
	}

	@Test
	void sendsObjectResultsAsJson() {
		List<org.springframework.ai.chat.messages.Message> sent = send(List.of(ContentItem.user("Weather"), first,
				ToolResult.success(first, new Weather("Berlin", 21))));

		assertThat(((ToolResponseMessage) sent.get(2)).getResponses().getFirst().responseData())
			.isEqualTo("{\"city\":\"Berlin\",\"temperature\":21}");
	}

	@Test
	void sendsAnEmptyTextForANullResult() {
		List<org.springframework.ai.chat.messages.Message> sent = send(List.of(ContentItem.user("Run"), first,
				ToolResult.success(first, null)));

		assertThat(((ToolResponseMessage) sent.get(2)).getResponses().getFirst().responseData()).isEmpty();
	}

	@Test
	void sendsTheTextOfAnError() {
		List<org.springframework.ai.chat.messages.Message> sent = send(List.of(ContentItem.user("Run"), first,
				ToolResult.error(first, "Tool 'x' not found")));

		assertThat(((ToolResponseMessage) sent.get(2)).getResponses().getFirst().responseData())
			.isEqualTo("Tool 'x' not found");
	}

	private List<org.springframework.ai.chat.messages.Message> send(List<ContentItem> contents) {
		when(openAi.call(any(Prompt.class)))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("done")))));
		client.generate(request("gpt-5-mini", contents, List.of(search)));
		ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(openAi).call(prompt.capture());
		return prompt.getValue().getInstructions();
	}

	private static LlmRequest request(String model, List<ContentItem> contents, List<ToolDefinition> tools) {
		LlmRequest request = new LlmRequest(model);
		request.contents().addAll(contents);
		request.tools().addAll(tools);
		request.toolChoice(ToolChoice.AUTO);
		return request;
	}
}
