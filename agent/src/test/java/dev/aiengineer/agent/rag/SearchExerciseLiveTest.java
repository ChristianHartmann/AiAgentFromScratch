package dev.aiengineer.agent.rag;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.aiengineer.agent.llm.LiveTests;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.ModelRouter;
import dev.aiengineer.agent.tool.WebSearchTools;
import dev.aiengineer.agent.tool.WebSearchTools.SearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The exercise of section 5.3.5 with real pages. The book goes from 37312 to 304 tokens
 * with Tavily; SearXNG and our own page loading give other numbers, the saving stays.
 */
@Tag("llm")
@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class SearchExerciseLiveTest {

	@Autowired
	private LlmClient llm;

	@Autowired
	private ModelRouter router;

	@Autowired
	private WebSearchTools webSearch;

	@Test
	void findsTheRelevantChunksInRealSearchResults() {
		LiveTests.assumeKeyPresentFor(router, "google/gemini-3.6-flash");
		LiveTests.assumeSearxngRunning();

		List<SearchResult> results = webSearch.searchWeb("2025 Nobel Prize winners", 10, null, null, true);
		List<Chunk> chunks = new ArrayList<>();
		for (SearchResult result : results) {
			Chunker.fixedLength("Title: " + result.title() + "\n" + result.rawContent(), Chunker.DEFAULT_SIZE,
					Chunker.DEFAULT_OVERLAP)
				.forEach(text -> chunks.add(new Chunk(text, Map.of("url", result.url()))));
		}
		int before = TokenCounter.count(results.stream().map(SearchResult::rawContent).collect(joining("\n")));
		assumeTrue(before > 3_000, "too little page content loaded: " + before + " tokens");

		List<ScoredChunk> top = new VectorSearch(llm).search("quantum computing", chunks, 3);

		int after = TokenCounter.count(top.stream().map(scored -> scored.chunk().text()).collect(joining("\n")));
		assertThat(top).hasSize(3);
		assertThat(after).isLessThan(before / 10);
	}
}
