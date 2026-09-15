package dev.aiengineer.agent.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.llm.EmbeddingPurpose;
import dev.aiengineer.agent.llm.LlmClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VectorSearchTest {

	private final LlmClient llm = mock(LlmClient.class);

	private final VectorSearch search = new VectorSearch(llm);

	private final List<Chunk> chunks = List.of(new Chunk("sideways"), new Chunk("same direction"),
			new Chunk("diagonal", Map.of("url", "https://example.com/diagonal")));

	@Test
	void ranksChunksByCosineSimilarityToTheQuery() {
		answer(new float[][] { { 1, 0 } }, new float[][] { { 0, 1 }, { 1, 0 }, { 0.7f, 0.7f } });

		List<ScoredChunk> result = search.search("query", chunks, 3);

		assertThat(result).extracting(scored -> scored.chunk().text())
			.containsExactly("same direction", "diagonal", "sideways");
		assertThat(result.getFirst().score()).isCloseTo(1.0, within(1e-6));
	}

	@Test
	void returnsOnlyTheBestTopK() {
		answer(new float[][] { { 1, 0 } }, new float[][] { { 0, 1 }, { 1, 0 }, { 0.7f, 0.7f } });

		assertThat(search.search("query", chunks, 1)).singleElement()
			.satisfies(scored -> assertThat(scored.chunk().text()).isEqualTo("same direction"));
	}

	@Test
	void keepsTheMetadataOfEveryChunk() {
		answer(new float[][] { { 1, 1 } }, new float[][] { { 0, 1 }, { 1, 0 }, { 0.7f, 0.7f } });

		assertThat(search.search("query", chunks, 1).getFirst().chunk().metadata())
			.containsEntry("url", "https://example.com/diagonal");
	}

	@Test
	void embedsTheQueryAsQueryAndTheChunksAsDocuments() {
		answer(new float[][] { { 1, 0 } }, new float[][] { { 0, 1 }, { 1, 0 }, { 0.7f, 0.7f } });

		search.search("query", chunks, 3);

		verify(llm).embed(List.of("query"), EmbeddingPurpose.QUERY);
		verify(llm).embed(List.of("sideways", "same direction", "diagonal"), EmbeddingPurpose.DOCUMENT);
	}

	@Test
	void findsNothingInNoChunks() {
		assertThat(search.search("query", List.of(), 3)).isEmpty();
		verifyNoInteractions(llm);
	}

	@Test
	void rejectsATopKBelowOne() {
		assertThatThrownBy(() -> search.search("query", chunks, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void treatsAZeroVectorAsUnrelated() {
		assertThat(VectorSearch.cosine(new float[] { 0, 0 }, new float[] { 1, 0 })).isZero();
	}

	private void answer(float[][] query, float[][] documents) {
		when(llm.embed(eq(List.of("query")), eq(EmbeddingPurpose.QUERY))).thenReturn(query);
		when(llm.embed(eq(chunks.stream().map(Chunk::text).toList()), eq(EmbeddingPurpose.DOCUMENT)))
			.thenReturn(documents);
	}
}
