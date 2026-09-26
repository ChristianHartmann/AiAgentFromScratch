package dev.aiengineer.agent.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.EmbeddingPurpose;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolResult;
import dev.aiengineer.agent.rag.KeywordEmbeddings;
import dev.aiengineer.agent.rag.SamplePages;
import dev.aiengineer.agent.rag.VectorSearch;
import dev.aiengineer.agent.tool.WebSearchTools.SearchResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SearchResultCompressorTest {

	private final LlmClient llm = mock(LlmClient.class);

	private final SearchResultCompressor compressor = new SearchResultCompressor(new VectorSearch(llm));

	private final ToolCall search = new ToolCall("call_1", "searchWeb", "{\"query\":\"quantum physics prize\"}");

	private final List<SearchResult> pages = SamplePages.ALL.stream()
		.map(page -> new SearchResult(page.title(), page.url(), "snippet of " + page.title(), page.text()))
		.toList();

	@Test
	void keepsTheThreeChunksClosestToTheQueryWithTitleAndUrl() {
		KeywordEmbeddings.answerFor(llm);

		Optional<ToolResult> compressed = compressor.afterTool(new ExecutionContext(), search,
				ToolResult.success(search, pages));

		assertThat(compressed).hasValueSatisfying(result -> {
			@SuppressWarnings("unchecked")
			List<SearchResult> hits = (List<SearchResult>) result.content();
			assertThat(hits).hasSize(SearchResultCompressor.TOP_K).allSatisfy(hit -> {
				assertThat(hit.title()).isEqualTo(SamplePages.PHYSICS.title());
				assertThat(hit.url()).isEqualTo(SamplePages.PHYSICS.url());
				assertThat(hit.content().length()).isLessThanOrEqualTo(500);
				assertThat(hit.rawContent()).isEmpty();
			});
		});
	}

	@Test
	void leavesShortResultsAsTheyAre() {
		List<SearchResult> snippets = List.of(new SearchResult("Title", "https://example.com", "A short snippet"));

		assertThat(compressor.afterTool(new ExecutionContext(), search, ToolResult.success(search, snippets))).isEmpty();
		verifyNoInteractions(llm);
	}

	@Test
	void comparesTheLengthOfTheTextNotTheNumberOfResults() {
		KeywordEmbeddings.answerFor(llm);
		List<SearchResult> two = pages.subList(0, 2);

		assertThat(compressor.afterTool(new ExecutionContext(), search, ToolResult.success(search, two))).isPresent();
	}

	@Test
	void leavesResultsOfOtherToolsAlone() {
		ToolCall read = new ToolCall("call_2", "readFile", "{\"filePath\":\"a.txt\"}");

		assertThat(compressor.afterTool(new ExecutionContext(), read, ToolResult.success(read, "x".repeat(5000))))
			.isEmpty();
	}

	@Test
	void leavesErrorsAlone() {
		assertThat(compressor.afterTool(new ExecutionContext(), search, ToolResult.error(search, "x".repeat(5000))))
			.isEmpty();
	}

	@Test
	void leavesTheResultAloneWithoutAQuery() {
		ToolCall noQuery = new ToolCall("call_3", "searchWeb", "{}");

		assertThat(compressor.afterTool(new ExecutionContext(), noQuery, ToolResult.success(noQuery, pages))).isEmpty();
	}

	@Test
	void fallsBackToTheSnippetsWhenEmbeddingFails() {
		when(llm.embed(anyList(), any(EmbeddingPurpose.class))).thenThrow(new IllegalStateException("429"));

		Optional<ToolResult> result = compressor.afterTool(new ExecutionContext(), search,
				ToolResult.success(search, pages));

		assertThat(result).hasValueSatisfying(fallback -> assertThat(fallback.content()).isEqualTo(List.of(
				new SearchResult(SamplePages.PHYSICS.title(), SamplePages.PHYSICS.url(), "snippet of " + SamplePages.PHYSICS.title()),
				new SearchResult(SamplePages.LITERATURE.title(), SamplePages.LITERATURE.url(), "snippet of " + SamplePages.LITERATURE.title()),
				new SearchResult(SamplePages.PEACE.title(), SamplePages.PEACE.url(), "snippet of " + SamplePages.PEACE.title()))));
	}
}
