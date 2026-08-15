package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class LlmServiceParallelTest {

	record Count(int value) {
	}

	private final ChatModel openAi = mock(ChatModel.class);

	private final LlmService service = new LlmService(new ModelRouter(Map.of(Provider.OPENAI, openAi)),
		new ConcurrencyProperties(Map.of(Provider.OPENAI, 3)));

	private final AtomicInteger inFlight = new AtomicInteger();

	private final AtomicInteger peak = new AtomicInteger();

	@Test
	void allowsNoMoreConcurrentCallsThanConfigured() {
		answerSlowly();

		List<LlmResult<Count>> results = service.completeAll("gpt-5-mini", questions(20), Count.class);

		assertThat(results).hasSize(20).allMatch(LlmResult::isSuccess);
		assertThat(peak.get()).isLessThanOrEqualTo(3);
	}

	@Test
	void actuallyRunsTheBatchInParallel() {
		answerSlowly();

		service.completeAll("gpt-5-mini", questions(20), Count.class);

		assertThat(peak.get()).isGreaterThan(1);
	}

	@Test
	void appliesTheLimitToSingleCallsFromDifferentThreadsToo() throws Exception {
		answerSlowly();

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			IntStream.range(0, 20).forEach(i -> executor.submit(
				() -> service.complete("gpt-5-mini", List.of(ChatMessage.user("Question " + i)))));
		}

		assertThat(peak.get()).isLessThanOrEqualTo(3);
	}

	@Test
	void returnsRemainingResultsWhenOneCallFails() {
		AtomicInteger calls = new AtomicInteger();
		when(openAi.call(any(Prompt.class))).thenAnswer(invocation -> {
			if (calls.incrementAndGet() == 2) {
				throw new IllegalStateException("Rate limit");
			}
			return answer("{\"value\":1}");
		});

		List<LlmResult<Count>> results = service.completeAll("gpt-5-mini", questions(3), Count.class);

		assertThat(results).hasSize(3);
		assertThat(results).filteredOn(LlmResult::isSuccess).hasSize(2);
		assertThat(results).filteredOn(result -> !result.isSuccess()).singleElement()
			.satisfies(result -> assertThat(result.error()).hasMessageContaining("Rate limit"));
	}

	@Test
	void keepsTheOrderOfTheRequests() {
		when(openAi.call(any(Prompt.class))).thenAnswer(invocation -> {
			Prompt prompt = invocation.getArgument(0);
			String number = prompt.getInstructions().getLast().getText();
			return answer("{\"value\":" + number + "}");
		});
		List<List<ChatMessage>> batch = IntStream.range(0, 10)
			.mapToObj(i -> List.of(ChatMessage.user(String.valueOf(i))))
			.toList();

		List<LlmResult<Count>> results = service.completeAll("gpt-5-mini", batch, Count.class);

		assertThat(results).extracting(result -> result.value().value())
			.containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
	}

	private void answerSlowly() {
		when(openAi.call(any(Prompt.class))).thenAnswer(invocation -> {
			peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
			Thread.sleep(50);
			inFlight.decrementAndGet();
			return answer("{\"value\":1}");
		});
	}

	private static List<List<ChatMessage>> questions(int count) {
		return IntStream.range(0, count)
			.mapToObj(i -> List.of(ChatMessage.user("Question " + i)))
			.toList();
	}

	private static ChatResponse answer(String json) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
	}
}
