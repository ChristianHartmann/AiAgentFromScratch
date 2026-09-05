package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

class LlmClientToolsTest {

	private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}";

	private final ToolDefinition search = new ToolDefinition("searchWeb", "Search the web", SCHEMA);

	private final ToolCall call = new ToolCall("call_1", "searchWeb", "{\"query\":\"Kipchoge\"}");

	private final ChatModel openAi = mock(ChatModel.class);

	private final ChatModel google = mock(ChatModel.class);

	private final LlmClient service = new LlmClient(
		new ModelRouter(Map.of(Provider.OPENAI, openAi, Provider.GOOGLE, google)), ConcurrencyProperties.defaults());

	@Test
	void sendsToolDefinitionsWithThePrompt() {
		answerWithText("ok");

		service.generate(request("gpt-5-mini", List.of(ContentItem.user("Hi")), List.of(search)));

		List<ToolCallback> callbacks = ((ToolCallingChatOptions) capturedPrompt().getOptions()).getToolCallbacks();
		assertThat(callbacks).singleElement().satisfies(callback -> {
			assertThat(callback.getToolDefinition().name()).isEqualTo("searchWeb");
			assertThat(callback.getToolDefinition().description()).isEqualTo("Search the web");
			assertThat(callback.getToolDefinition().inputSchema()).isEqualTo(SCHEMA);
		});
	}

	@Test
	void neverLetsSpringAiExecuteATool() {
		answerWithText("ok");

		service.generate(request("gpt-5-mini", List.of(ContentItem.user("Hi")), List.of(search)));

		ToolCallback callback = ((ToolCallingChatOptions) capturedPrompt().getOptions()).getToolCallbacks().getFirst();
		assertThatThrownBy(() -> callback.call("{}")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void returnsTheToolCallsOfTheAnswer() {
		when(openAi.call(any(Prompt.class))).thenReturn(response(AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "searchWeb", "{\"query\":\"Kipchoge\"}")))
			.build()));

		LlmResponse response = service.generate(request("gpt-5-mini", List.of(ContentItem.user("Hi")), List.of(search)));

		assertThat(response.hasToolCalls()).isTrue();
		assertThat(response.toolCalls()).containsExactly(call);
		assertThat(response.text()).isEmpty();
	}

	@Test
	void assignsAnIdWhenTheProviderSendsNone() {
		when(openAi.call(any(Prompt.class))).thenReturn(response(AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(
				new AssistantMessage.ToolCall("", "function", "searchWeb", "{\"query\":\"Kipchoge\"}"),
				new AssistantMessage.ToolCall(null, "function", "searchWeb", "{\"query\":\"Kiptum\"}")))
			.build()));

		LlmResponse response = service.generate(request("gpt-5-mini", List.of(ContentItem.user("Hi")), List.of(search)));

		assertThat(response.toolCalls()).extracting(ToolCall::id)
			.allSatisfy(id -> assertThat(id).startsWith("call_").hasSizeGreaterThan("call_".length()))
			.doesNotHaveDuplicates();
	}

	@Test
	void carriesTheProviderStateOfAnAnswerIntoTheNextCall() {
		when(openAi.call(any(Prompt.class)))
			.thenReturn(response(AssistantMessage.builder()
				.content("")
				.toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "searchWeb", "{}")))
				.properties(Map.of("thoughtSignatures", List.of("opaque-signature")))
				.build()))
			.thenReturn(response(new AssistantMessage("done")));
		Conversation conversation = new Conversation();
		conversation.addUser("Search");

		LlmResponse first = service.generate(request("gpt-5-mini", conversation.messages(), List.of(search)));
		first.content().forEach(conversation::add);
		conversation.add(ToolResult.success(first.toolCalls().getFirst(), "result"));
		service.generate(request("gpt-5-mini", conversation.messages(), List.of(search)));

		ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
		verify(openAi, times(2)).call(prompts.capture());
		AssistantMessage replayed = (AssistantMessage) prompts.getAllValues().get(1).getInstructions().get(1);
		assertThat(replayed.getMetadata()).containsEntry("thoughtSignatures", List.of("opaque-signature"));
	}

	@Test
	void returnsTextWhenTheModelAnswersDirectly() {
		answerWithText("Seoul");

		LlmResponse response = service.generate(request("gpt-5-mini", List.of(ContentItem.user("Capital?")), List.of(search)));

		assertThat(response.hasToolCalls()).isFalse();
		assertThat(response.text()).isEqualTo("Seoul");
	}

	@Test
	void passesToolCallsAndToolResultsOfTheHistoryToSpringAi() {
		answerWithText("done");

		service.generate(request("gpt-5-mini", List.of(
			ContentItem.user("Search"),
			call,
			ToolResult.success(call, "Kipchoge ran 2:01:09")), List.of(search)));

		List<Message> instructions = capturedPrompt().getInstructions();
		assertThat(instructions).extracting(Message::getMessageType)
			.containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL);
		assertThat(((AssistantMessage) instructions.get(1)).getToolCalls()).singleElement()
			.satisfies(toolCall -> {
				assertThat(toolCall.id()).isEqualTo("call_1");
				assertThat(toolCall.name()).isEqualTo("searchWeb");
				assertThat(toolCall.arguments()).isEqualTo("{\"query\":\"Kipchoge\"}");
			});
		assertThat(((ToolResponseMessage) instructions.get(2)).getResponses()).containsExactly(
			new ToolResponseMessage.ToolResponse("call_1", "searchWeb", "Kipchoge ran 2:01:09"));
	}

	@Test
	void mergesConsecutiveToolResultsIntoOneResponseMessage() {
		answerWithText("done");
		ToolCall second = new ToolCall("call_2", "searchWeb", "{\"query\":\"Kiptum\"}");

		service.generate(request("gpt-5-mini", List.of(
			ContentItem.user("Search twice"),
			call, second,
			ToolResult.success(call, "first"),
			ToolResult.success(second, "second")), List.of(search)));

		List<Message> instructions = capturedPrompt().getInstructions();
		assertThat(instructions).extracting(Message::getMessageType)
			.containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL);
		assertThat(((ToolResponseMessage) instructions.get(2)).getResponses())
			.extracting(ToolResponseMessage.ToolResponse::id).containsExactly("call_1", "call_2");
	}

	@Test
	void encodesPlainTextToolResultsAsJsonForGoogle() {
		assertThat(toolResultSentToGoogle("Suggestions: nobel prize 2025")).isEqualTo("\"Suggestions: nobel prize 2025\"");
	}

	@Test
	void keepsToolResultsThatAreJsonForGoogle() {
		assertThat(toolResultSentToGoogle("{\"city\":\"Berlin\"}")).isEqualTo("{\"city\":\"Berlin\"}");
		assertThat(toolResultSentToGoogle("7006652.0")).isEqualTo("7006652.0");
	}

	@Test
	void encodesTextThatOnlyStartsLikeJsonForGoogle() {
		assertThat(toolResultSentToGoogle("3 results found")).isEqualTo("\"3 results found\"");
	}

	@Test
	void encodesAnEmptyToolResultForGoogle() {
		assertThat(toolResultSentToGoogle("")).isEqualTo("\"\"");
	}

	@Test
	void keepsPlainTextToolResultsForOtherProviders() {
		answerWithText("done");

		service.generate(request("gpt-5-mini", List.of(
			ContentItem.user("Search"),
			call,
			ToolResult.success(call, "Suggestions: nobel prize 2025")), List.of(search)));

		assertThat(((ToolResponseMessage) capturedPrompt().getInstructions().get(2)).getResponses().getFirst()
			.responseData()).isEqualTo("Suggestions: nobel prize 2025");
	}

	@Test
	void reportsARefusalOnlyWhenThereIsNeitherTextNorToolCalls() {
		answerWithText("");

		assertThatThrownBy(() -> service.generate(request("gpt-5-mini", List.of(ContentItem.user("Hi")), List.of(search))))
			.isInstanceOf(LlmRefusalException.class);
	}

	/**
	 * Spring AI parses every tool result for Gemini as JSON, plain text makes it fail.
	 */
	private String toolResultSentToGoogle(String content) {
		when(google.call(any(Prompt.class))).thenReturn(response(new AssistantMessage("done")));

		service.generate(request("google/gemini-3.6-flash", List.of(
			ContentItem.user("Search"),
			call,
			ToolResult.success(call, content)), List.of(search)));

		ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
		verify(google, atLeastOnce()).call(prompts.capture());
		return ((ToolResponseMessage) prompts.getAllValues().getLast().getInstructions().get(2)).getResponses()
			.getFirst()
			.responseData();
	}

	@Test
	void sendsEveryInstructionAsItsOwnSystemMessage() {
		answerWithText("ok");
		LlmRequest request = new LlmRequest("gpt-5-mini");
		request.instructions().addAll(List.of("You are helpful.", "Answer briefly."));
		request.contents().add(ContentItem.user("Hi"));

		service.generate(request);

		assertThat(capturedPrompt().getInstructions()).extracting(Message::getMessageType)
			.containsExactly(MessageType.SYSTEM, MessageType.SYSTEM, MessageType.USER);
	}

	@Test
	void answersWithAnAssistantMessageFollowedByTheToolCalls() {
		when(openAi.call(any(Prompt.class))).thenReturn(response(AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "searchWeb", "{}")))
			.build()));

		LlmResponse response = service.generate(request("gpt-5-mini", List.of(ContentItem.user("Hi")), List.of(search)));

		assertThat(response.content()).hasSize(2);
		assertThat(response.content().getFirst()).isInstanceOfSatisfying(dev.aiengineer.agent.llm.Message.class,
				message -> assertThat(message.role()).isEqualTo(Role.ASSISTANT));
		assertThat(response.content().get(1)).isInstanceOf(ToolCall.class);
	}

	@Test
	void reportsTheUsageOfTheCall() {
		when(openAi.call(any(Prompt.class))).thenReturn(new ChatResponse(
				List.of(new Generation(new AssistantMessage("ok"))),
				ChatResponseMetadata.builder().usage(new DefaultUsage(12, 3)).build()));

		LlmResponse response = service.generate(request("gpt-5-mini", List.of(ContentItem.user("Hi")), List.of()));

		assertThat(response.usage()).isEqualTo(new Usage(12, 3));
	}

	private void answerWithText(String text) {
		when(openAi.call(any(Prompt.class))).thenReturn(response(new AssistantMessage(text)));
	}

	private static ChatResponse response(AssistantMessage message) {
		return new ChatResponse(List.of(new Generation(message)));
	}

	private Prompt capturedPrompt() {
		ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(openAi).call(prompt.capture());
		return prompt.getValue();
	}

	private static LlmRequest request(String model, List<ContentItem> contents, List<ToolDefinition> tools) {
		LlmRequest request = new LlmRequest(model);
		request.contents().addAll(contents);
		request.tools().addAll(tools);
		request.toolChoice(ToolChoice.AUTO);
		return request;
	}
}
