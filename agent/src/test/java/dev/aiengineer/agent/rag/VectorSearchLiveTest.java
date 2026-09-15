package dev.aiengineer.agent.rag;

import static org.assertj.core.api.Assertions.assertThat;

import dev.aiengineer.agent.llm.LiveTests;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.ModelRouter;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Listing 5.6 with Gemini. The book ranks deep learning, machine learning, Python and cats
 * with 0.353 down to 0.048; with gemini-embedding-001 the numbers differ, the order holds.
 */
@Tag("llm")
@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class VectorSearchLiveTest {

	@Autowired
	private LlmClient llm;

	@Autowired
	private ModelRouter router;

	@Test
	void ranksDocumentsAboutArtificialIntelligenceFirst() {
		LiveTests.assumeKeyPresentFor(router, "google/gemini-3.6-flash");
		List<Chunk> documents = List.of(
			new Chunk("Python is a popular programming language."),
			new Chunk("Machine learning uses Python for data analysis and model training."),
			new Chunk("Cats are independent animals that sleep most of the day."),
			new Chunk("Deep learning is a subfield of machine learning based on neural networks."));

		List<ScoredChunk> result = new VectorSearch(llm).search("Artificial Intelligence", documents, 4);

		assertThat(result.getLast().chunk().text()).startsWith("Cats");
		assertThat(result.getFirst().chunk().text()).startsWith("Deep learning");
	}
}
