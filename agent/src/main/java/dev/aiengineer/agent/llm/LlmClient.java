package dev.aiengineer.agent.llm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The client for all LLM calls, the LlmClient of section 4.5.4. Plays the role LiteLLM plays
 * in the book: callers name a model, everything provider specific happens behind this class.
 *
 * <p>Every call waits for a free slot of its provider first, so the configured limit holds
 * across batches and single calls from different threads alike.
 */
@Service
public class LlmClient {

	private static final JsonMapper STRICT_JSON = JsonMapper.builder()
		.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
		.build();

	private final ModelRouter router;

	private final Map<Provider, Semaphore> slots = new EnumMap<>(Provider.class);

	public LlmClient(ModelRouter router, ConcurrencyProperties concurrency) {
		this.router = router;
		Arrays.stream(Provider.values())
			.forEach(provider -> slots.put(provider, new Semaphore(concurrency.limit(provider))));
	}

	public String complete(String model, List<ContentItem> messages) {
		return textOf(model, call(model, messages, router.options(model)).getResult());
	}

	/**
	 * Asks for an answer in the shape of the given record. The JSON schema of the record
	 * goes to the provider's native structured output, the answer is converted back.
	 */
	public <T> T complete(String model, List<ContentItem> messages, Class<T> responseType) {
		BeanOutputConverter<T> converter = new BeanOutputConverter<>(responseType);
		String answer = textOf(model,
				call(model, messages, router.options(model, converter.getJsonSchema())).getResult());
		return converter.convert(answer);
	}

	/**
	 * Sends the request and returns the answer without running any tool: an assistant
	 * message with the text and the provider state, followed by the tool calls, if any.
	 */
	public LlmResponse generate(LlmRequest request) {
		List<ContentItem> contents = new ArrayList<>();
		request.instructions().forEach(instruction -> contents.add(ContentItem.system(instruction)));
		contents.addAll(request.contents());
		ChatOptions options = router.options(request.model(), request.tools(), request.toolChoice());
		ChatResponse response = call(request.model(), contents, options);
		Generation generation = response.getResult();
		AssistantMessage output = generation.getOutput();
		String text = output.getText() == null ? "" : output.getText();
		List<ToolCall> toolCalls = output.getToolCalls().stream()
			.map(call -> new ToolCall(idOf(call), call.name(), call.arguments()))
			.toList();
		if (text.isBlank() && toolCalls.isEmpty()) {
			throw new LlmRefusalException(request.model(), generation.getMetadata().getFinishReason());
		}
		List<ContentItem> answer = new ArrayList<>();
		answer.add(new Message(Role.ASSISTANT, text, output.getMetadata()));
		answer.addAll(toolCalls);
		return new LlmResponse(answer, usageOf(response));
	}

	/**
	 * Sends every conversation of the batch concurrently and returns the results in the
	 * order of the batch. A failing call ends up as a failed result, the others go on.
	 */
	public <T> List<LlmResult<T>> completeAll(String model, List<List<ContentItem>> batch, Class<T> responseType) {
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<T>> futures = batch.stream()
				.map(messages -> executor.submit(() -> complete(model, messages, responseType)))
				.toList();
			return futures.stream().map(LlmClient::result).toList();
		}
	}

	/**
	 * Every tool call gets an id, so each result can be tied to its call even when the model
	 * calls the same tool twice in one answer. Spring AI leaves the id empty for Gemini: it
	 * reads only name and arguments of a function call and matches results by name and order.
	 */
	private static String idOf(AssistantMessage.ToolCall call) {
		return call.id() == null || call.id().isBlank() ? "call_" + UUID.randomUUID() : call.id();
	}

	private ChatResponse call(String model, List<ContentItem> messages, ChatOptions options) {
		Semaphore slot = slots.get(router.provider(model));
		try {
			slot.acquire();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for a free slot for " + model, ex);
		}
		try {
			Prompt prompt = new Prompt(toSpringAi(router.provider(model), messages), options);
			return router.chatModel(model).call(prompt);
		}
		finally {
			slot.release();
		}
	}

	private static Usage usageOf(ChatResponse response) {
		if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
			return Usage.NONE;
		}
		org.springframework.ai.chat.metadata.Usage usage = response.getMetadata().getUsage();
		return new Usage(usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
				usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
	}

	private static String textOf(String model, Generation generation) {
		String text = generation.getOutput().getText();
		if (text == null || text.isBlank()) {
			throw new LlmRefusalException(model, generation.getMetadata().getFinishReason());
		}
		return text;
	}

	private static <T> LlmResult<T> result(Future<T> future) {
		try {
			return LlmResult.success(future.get());
		}
		catch (ExecutionException ex) {
			return LlmResult.failure(ex.getCause() instanceof Exception cause ? cause : ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return LlmResult.failure(ex);
		}
	}

	/**
	 * Translates our content items into Spring AI's messages. An assistant message and the
	 * tool calls that follow it become one message, as the book merges text and tool calls in
	 * build_messages. Consecutive tool results belong to one answer of the model and go back
	 * together in a single tool response, as Gemini expects it.
	 */
	private static List<org.springframework.ai.chat.messages.Message> toSpringAi(Provider provider,
			List<ContentItem> contents) {
		List<org.springframework.ai.chat.messages.Message> result = new ArrayList<>();
		PendingAssistant assistant = null;
		List<ToolResponseMessage.ToolResponse> toolResults = new ArrayList<>();
		for (ContentItem item : contents) {
			if (!(item instanceof ToolResult)) {
				flushToolResults(toolResults, result);
			}
			if (!(item instanceof ToolCall) && assistant != null) {
				result.add(assistant.build());
				assistant = null;
			}
			switch (item) {
				case Message message when message.role() == Role.ASSISTANT -> assistant = new PendingAssistant(message);
				case Message message when message.role() == Role.SYSTEM -> result.add(new SystemMessage(message.content()));
				case Message message -> result.add(new UserMessage(message.content()));
				case ToolCall call -> {
					if (assistant == null) {
						assistant = new PendingAssistant(new Message(Role.ASSISTANT, ""));
					}
					assistant.toolCalls.add(new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.arguments()));
				}
				case ToolResult toolResult -> toolResults.add(new ToolResponseMessage.ToolResponse(
						toolResult.toolCallId(), toolResult.name(), textFor(provider, toolResult.content())));
			}
		}
		if (assistant != null) {
			result.add(assistant.build());
		}
		flushToolResults(toolResults, result);
		return result;
	}

	private static final class PendingAssistant {

		private final Message message;

		private final List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

		PendingAssistant(Message message) {
			this.message = message;
		}

		AssistantMessage build() {
			return AssistantMessage.builder()
				.content(message.content())
				.properties(message.providerState())
				.toolCalls(toolCalls)
				.build();
		}
	}

	/**
	 * Tool results reach the model as text: strings as they are, other objects as JSON, null
	 * as empty text. Gemini additionally needs every result to be a JSON document.
	 */
	private static String textFor(Provider provider, Object content) {
		String text = switch (content) {
			case null -> "";
			case String string -> string;
			default -> STRICT_JSON.writeValueAsString(content);
		};
		return provider == Provider.GOOGLE ? asJsonDocument(text) : text;
	}

	private static void flushToolResults(List<ToolResponseMessage.ToolResponse> toolResults,
			List<org.springframework.ai.chat.messages.Message> result) {
		if (!toolResults.isEmpty()) {
			result.add(ToolResponseMessage.builder().responses(List.copyOf(toolResults)).build());
			toolResults.clear();
		}
	}

	/**
	 * Spring AI parses every tool result for Gemini as JSON and fails on plain text, which is
	 * what MCP servers usually return. Text that is not a complete JSON document therefore goes
	 * out as JSON string. Trailing text counts as not JSON, so "3 results found" does not turn
	 * into the number 3.
	 */
	private static String asJsonDocument(String content) {
		if (!content.isBlank()) {
			try {
				STRICT_JSON.readTree(content);
				return content;
			}
			catch (JacksonException ex) {
				// not JSON, encoded below
			}
		}
		return STRICT_JSON.writeValueAsString(content);
	}
}
