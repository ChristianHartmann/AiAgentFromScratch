package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType;

class LlmClientEmbeddingTest {

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	private final LlmClient client = new LlmClient(new ModelRouter(Map.of()), ConcurrencyProperties.defaults(),
			embeddingModel);

	@Test
	void returnsOneVectorPerTextInTheOrderOfTheTexts() {
		answerWithTheNumberInEachText();

		float[][] vectors = client.embed(List.of("0", "1", "2"), EmbeddingPurpose.DOCUMENT);

		assertThat(vectors.length).isEqualTo(3);
		assertThat(vectors[2][0]).isEqualTo(2f);
	}

	@Test
	void sendsAtMostOneHundredTextsPerCall() {
		answerWithTheNumberInEachText();
		List<String> texts = IntStream.range(0, 250).mapToObj(String::valueOf).toList();

		float[][] vectors = client.embed(texts, EmbeddingPurpose.DOCUMENT);

		ArgumentCaptor<EmbeddingRequest> requests = ArgumentCaptor.forClass(EmbeddingRequest.class);
		verify(embeddingModel, times(3)).call(requests.capture());
		assertThat(requests.getAllValues()).extracting(request -> request.getInstructions().size())
			.containsExactly(100, 100, 50);
		assertThat(vectors[249][0]).isEqualTo(249f);
	}

	@Test
	void tellsGeminiWhetherItEmbedsAQuestionOrPassages() {
		answerWithTheNumberInEachText();

		client.embed(List.of("1"), EmbeddingPurpose.QUERY);
		client.embed(List.of("2"), EmbeddingPurpose.DOCUMENT);

		ArgumentCaptor<EmbeddingRequest> requests = ArgumentCaptor.forClass(EmbeddingRequest.class);
		verify(embeddingModel, times(2)).call(requests.capture());
		assertThat(requests.getAllValues())
			.extracting(request -> ((GoogleGenAiTextEmbeddingOptions) request.getOptions()).getTaskType())
			.containsExactly(TaskType.RETRIEVAL_QUERY, TaskType.RETRIEVAL_DOCUMENT);
	}

	@Test
	void callsNothingForNoTexts() {
		assertThat(client.embed(List.of(), EmbeddingPurpose.QUERY)).isEmpty();
		verifyNoInteractions(embeddingModel);
	}

	@Test
	void rejectsAnAnswerWithFewerVectorsThanTexts() {
		when(embeddingModel.call(any(EmbeddingRequest.class)))
			.thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] { 1f }, 0))));

		assertThatThrownBy(() -> client.embed(List.of("a", "b"), EmbeddingPurpose.DOCUMENT))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("2");
	}

	@Test
	void explainsThatAClientWithoutEmbeddingModelCannotEmbed() {
		LlmClient chatOnly = new LlmClient(new ModelRouter(Map.of()), ConcurrencyProperties.defaults());

		assertThatThrownBy(() -> chatOnly.embed(List.of("a"), EmbeddingPurpose.QUERY))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("embedding model");
	}

	private void answerWithTheNumberInEachText() {
		when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
			EmbeddingRequest request = invocation.getArgument(0);
			List<Embedding> embeddings = new ArrayList<>();
			for (int i = 0; i < request.getInstructions().size(); i++) {
				embeddings.add(new Embedding(new float[] { Float.parseFloat(request.getInstructions().get(i)) }, i));
			}
			return new EmbeddingResponse(embeddings);
		});
	}
}
