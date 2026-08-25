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

/**
 * The single entry point for LLM calls. Plays the role LiteLLM plays in the book:
 * callers name a model, everything provider specific happens behind this class.
 *
 * <p>Every call waits for a free slot of its provider first, so the configured limit holds
 * across batches and single calls from different threads alike.
 */
@Service
public class LlmService {

	private final ModelRouter router;

	private final Map<Provider, Semaphore> slots = new EnumMap<>(Provider.class);

	public LlmService(ModelRouter router, ConcurrencyProperties concurrency) {
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
			return futures.stream().map(LlmService::result).toList();
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
			return router.chatModel(model).call(new Prompt(toSpringAi(messages), options)).getResult();
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
	private static List<Message> toSpringAi(List<ChatMessage> messages) {
		List<Message> result = new ArrayList<>();
		List<ToolResponseMessage.ToolResponse> pendingToolResults = new ArrayList<>();
		for (ChatMessage message : messages) {
			if (message instanceof ChatMessage.ToolResultMessage toolResult) {
				pendingToolResults.add(new ToolResponseMessage.ToolResponse(
						toolResult.toolCallId(), toolResult.toolName(), toolResult.content()));
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

	private static void flush(List<ToolResponseMessage.ToolResponse> pendingToolResults, List<Message> result) {
		if (!pendingToolResults.isEmpty()) {
			result.add(ToolResponseMessage.builder().responses(List.copyOf(pendingToolResults)).build());
			pendingToolResults.clear();
		}
	}
}
