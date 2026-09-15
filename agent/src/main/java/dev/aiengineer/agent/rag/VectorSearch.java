package dev.aiengineer.agent.rag;

import dev.aiengineer.agent.llm.EmbeddingPurpose;
import dev.aiengineer.agent.llm.LlmClient;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Vector search of section 5.3.3: embed the query and the chunks, compare by cosine
 * similarity, keep the best. Linear over all chunks, as in the book; section 5.3.4 explains
 * why larger collections need a vector database.
 */
public final class VectorSearch {

	private final LlmClient llm;

	public VectorSearch(LlmClient llm) {
		this.llm = llm;
	}

	public List<ScoredChunk> search(String query, List<Chunk> chunks, int topK) {
		if (topK < 1) {
			throw new IllegalArgumentException("topK must be at least 1, was " + topK);
		}
		if (chunks.isEmpty()) {
			return List.of();
		}
		float[] queryVector = llm.embed(List.of(query), EmbeddingPurpose.QUERY)[0];
		float[][] chunkVectors = llm.embed(chunks.stream().map(Chunk::text).toList(), EmbeddingPurpose.DOCUMENT);
		return IntStream.range(0, chunks.size())
			.mapToObj(i -> new ScoredChunk(chunks.get(i), cosine(queryVector, chunkVectors[i])))
			.sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
			.limit(topK)
			.toList();
	}

	/**
	 * Cosine similarity; a zero vector points nowhere and counts as unrelated.
	 */
	public static double cosine(float[] a, float[] b) {
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			normA += a[i] * a[i];
			normB += b[i] * b[i];
		}
		return normA == 0 || normB == 0 ? 0 : dot / Math.sqrt(normA * normB);
	}
}
