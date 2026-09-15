package dev.aiengineer.agent.rag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.llm.EmbeddingPurpose;
import dev.aiengineer.agent.llm.LlmClient;
import java.util.List;
import java.util.Locale;

/**
 * Fake embeddings for tests: one dimension per keyword, counting how often it occurs. Texts
 * about the same keywords point in the same direction, so vector search behaves predictably
 * without a model.
 */
public final class KeywordEmbeddings {

	private static final List<String> KEYWORDS = List.of("quantum", "physics", "literature", "novel", "peace",
			"treaty");

	private KeywordEmbeddings() {
	}

	public static void answerFor(LlmClient llm) {
		when(llm.embed(anyList(), any(EmbeddingPurpose.class)))
			.thenAnswer(invocation -> embed(invocation.<List<String>>getArgument(0)));
	}

	public static float[][] embed(List<String> texts) {
		float[][] vectors = new float[texts.size()][];
		for (int i = 0; i < texts.size(); i++) {
			String text = texts.get(i).toLowerCase(Locale.ROOT);
			float[] vector = new float[KEYWORDS.size() + 1];
			for (int k = 0; k < KEYWORDS.size(); k++) {
				vector[k] = text.split(KEYWORDS.get(k), -1).length - 1;
			}
			vector[KEYWORDS.size()] = 0.1f;
			vectors[i] = vector;
		}
		return vectors;
	}
}
