package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Listing 5.2 with Gemini. The book prints 0.385 and 0.200 for text-embedding-3-small;
 * gemini-embedding-001 gives other numbers, only the order carries over.
 */
@Tag("llm")
@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class EmbeddingLiveTest {

	@Autowired
	private LlmClient llm;

	@Autowired
	private ModelRouter router;

	@Test
	void ranksTheKittenCloserToTheCatThanTheDog() {
		LiveTests.assumeKeyPresentFor(router, "google/gemini-3.6-flash");

		float[][] vectors = llm.embed(List.of("The cat is sleeping on the sofa.",
				"A kitten is playing with a ball of yarn.", "The dog is running in the park."),
				EmbeddingPurpose.DOCUMENT);

		assertThat(vectors[0]).hasSize(3072);
		assertThat(cosine(vectors[0], vectors[1])).isGreaterThan(cosine(vectors[0], vectors[2]));
	}

	private static double cosine(float[] a, float[] b) {
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			normA += a[i] * a[i];
			normB += b[i] * b[i];
		}
		return dot / Math.sqrt(normA * normB);
	}
}
