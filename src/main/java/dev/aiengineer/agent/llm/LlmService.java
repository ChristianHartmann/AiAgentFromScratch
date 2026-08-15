package dev.aiengineer.agent.llm;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
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
		return call(model, messages, router.options(model));
	}

	/**
	 * Asks for an answer in the shape of the given record. The JSON schema of the record
	 * goes to the provider's native structured output, the answer is converted back.
	 */
	public <T> T complete(String model, List<ChatMessage> messages, Class<T> responseType) {
		BeanOutputConverter<T> converter = new BeanOutputConverter<>(responseType);
		String answer = call(model, messages, router.options(model, converter.getJsonSchema()));
		return converter.convert(answer);
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

	private String call(String model, List<ChatMessage> messages, ChatOptions options) {
		Semaphore slot = slots.get(router.provider(model));
		try {
			slot.acquire();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for a free slot for " + model, ex);
		}
		try {
			ChatResponse response = router.chatModel(model).call(new Prompt(toSpringAi(messages), options));
			return response.getResult().getOutput().getText();
		}
		finally {
			slot.release();
		}
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

	private static List<Message> toSpringAi(List<ChatMessage> messages) {
		return messages.stream().map(LlmService::toSpringAi).toList();
	}

	private static Message toSpringAi(ChatMessage message) {
		return switch (message.role()) {
			case SYSTEM -> new SystemMessage(message.content());
			case USER -> new UserMessage(message.content());
			case ASSISTANT -> new AssistantMessage(message.content());
		};
	}
}
