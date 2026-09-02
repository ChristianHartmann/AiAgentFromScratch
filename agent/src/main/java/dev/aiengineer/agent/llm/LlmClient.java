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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
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

	public String complete(String model, List<ChatMessage> messages) {
		return textOf(model, call(model, messages, router.options(model)));
	}

	/**
	 * Asks for an answer in the shape of the given record. The JSON schema of the record
	 * goes to the provider's native structured output, the answer is converted back.
	 */
	public <T> T complete(String model, List<ChatMessage> messages, Class<T> responseType) {
		BeanOutputConverter<T> converter = new BeanOutputConverter<>(responseType);
		String answer = textOf(model, call(model, messages, router.options(model, converter.getJsonSchema())));
		return converter.convert(answer);
	}

	/**
	 * Offers the tools to the model and returns its answer without running any tool: either
	 * text, or the tool calls the caller has to execute and feed back as tool results.
	 */
	public LlmResponse respond(String model, List<ChatMessage> messages, List<ToolDefinition> tools) {
		Generation generation = call(model, messages, router.options(model, tools));
		AssistantMessage output = generation.getOutput();
		List<ToolCall> toolCalls = output.getToolCalls().stream()
			.map(call -> new ToolCall(idOf(call), call.name(), call.arguments()))
			.toList();
		String text = output.getText() == null ? "" : output.getText();
		if (text.isBlank() && toolCalls.isEmpty()) {
			throw new LlmRefusalException(model, generation.getMetadata().getFinishReason());
		}
		return new LlmResponse(text, toolCalls, output.getMetadata());
	}

	/**
	 * Sends every conversation of the batch concurrently and returns the results in the
	 * order of the batch. A failing call ends up as a failed result, the others go on.
	 */
	public <T> List<LlmResult<T>> completeAll(String model, List<List<ChatMessage>> batch, Class<T> responseType) {
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

	private Generation call(String model, List<ChatMessage> messages, ChatOptions options) {
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
			return router.chatModel(model).call(prompt).getResult();
		}
		finally {
			slot.release();
		}
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
	 * Translates our messages into Spring AI's. Consecutive tool results belong to one answer
	 * of the model and go back together in a single tool response, as Gemini expects it.
	 */
	private static List<Message> toSpringAi(Provider provider, List<ChatMessage> messages) {
		List<Message> result = new ArrayList<>();
		List<ToolResponseMessage.ToolResponse> pendingToolResults = new ArrayList<>();
		for (ChatMessage message : messages) {
			if (message instanceof ChatMessage.ToolResultMessage toolResult) {
				String content = provider == Provider.GOOGLE ? asJsonDocument(toolResult.content()) : toolResult.content();
				pendingToolResults.add(new ToolResponseMessage.ToolResponse(
						toolResult.toolCallId(), toolResult.toolName(), content));
				continue;
			}
			flush(pendingToolResults, result);
			result.add(switch (message) {
				case ChatMessage.SystemMessage system -> new SystemMessage(system.content());
				case ChatMessage.UserMessage user -> new UserMessage(user.content());
				case ChatMessage.AssistantMessage assistant -> AssistantMessage.builder()
					.content(assistant.content())
					.properties(assistant.providerState())
					.toolCalls(assistant.toolCalls().stream()
						.map(call -> new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.arguments()))
						.toList())
					.build();
				case ChatMessage.ToolResultMessage _ -> throw new IllegalStateException("handled above");
			});
		}
		flush(pendingToolResults, result);
		return result;
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

	private static void flush(List<ToolResponseMessage.ToolResponse> pendingToolResults, List<Message> result) {
		if (!pendingToolResults.isEmpty()) {
			result.add(ToolResponseMessage.builder().responses(List.copyOf(pendingToolResults)).build());
			pendingToolResults.clear();
		}
	}
}
