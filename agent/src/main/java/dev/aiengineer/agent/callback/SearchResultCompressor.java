package dev.aiengineer.agent.callback;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolResult;
import dev.aiengineer.agent.rag.Chunk;
import dev.aiengineer.agent.rag.Chunker;
import dev.aiengineer.agent.rag.VectorSearch;
import dev.aiengineer.agent.tool.WebSearchTools;
import dev.aiengineer.agent.tool.WebSearchTools.SearchResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shortens long search results to the chunks closest to the query, section 5.5.4. Unlike
 * Listing 5.29 it chunks every result on its own, so title and URL stay with each chunk, and
 * the result stays a list of search results. The repository's version never compresses: it
 * compares the number of results with the threshold of 2000 characters.
 */
public class SearchResultCompressor implements AfterToolCallback {

	static final int THRESHOLD = 2000;

	static final int TOP_K = 3;

	private static final JsonMapper JSON = JsonMapper.shared();

	private final VectorSearch vectorSearch;

	public SearchResultCompressor(VectorSearch vectorSearch) {
		this.vectorSearch = vectorSearch;
	}

	@Override
	public Optional<ToolResult> afterTool(ExecutionContext context, ToolCall call, ToolResult result) {
		if (!WebSearchTools.SEARCH_WEB.equals(call.name()) || result.status() != ToolResult.Status.SUCCESS
				|| !(result.content() instanceof List<?> items)
				|| !items.stream().allMatch(SearchResult.class::isInstance)) {
			return Optional.empty();
		}
		List<SearchResult> hits = items.stream().map(SearchResult.class::cast).toList();
		String query = queryOf(call);
		if (query == null || lengthOf(hits) < THRESHOLD) {
			return Optional.empty();
		}
		try {
			return Optional.of(ToolResult.success(call, closestChunks(query, hits)));
		}
		catch (RuntimeException ex) {
			return Optional.of(ToolResult.success(call, snippetsOf(hits)));
		}
	}

	private List<SearchResult> closestChunks(String query, List<SearchResult> hits) {
		List<Chunk> chunks = hits.stream()
			.flatMap(hit -> Chunker.fixedLength("Title: " + hit.title() + "\n" + textOf(hit), Chunker.DEFAULT_SIZE,
					Chunker.DEFAULT_OVERLAP)
				.stream()
				.map(text -> new Chunk(text, Map.of("title", Objects.toString(hit.title(), ""), "url",
						Objects.toString(hit.url(), "")))))
			.toList();
		return vectorSearch.search(query, chunks, TOP_K).stream()
			.map(scored -> new SearchResult(scored.chunk().metadata().get("title"), scored.chunk().metadata().get("url"),
					scored.chunk().text()))
			.toList();
	}

	/**
	 * Without embeddings the page contents cannot be shortened; sending them whole could put
	 * half a million characters into the context, so only the snippets remain.
	 */
	private static List<SearchResult> snippetsOf(List<SearchResult> hits) {
		return hits.stream().map(hit -> new SearchResult(hit.title(), hit.url(), hit.content())).toList();
	}

	private static String textOf(SearchResult hit) {
		return hit.rawContent().isBlank() ? Objects.toString(hit.content(), "") : hit.rawContent();
	}

	private static int lengthOf(List<SearchResult> hits) {
		return hits.stream()
			.mapToInt(hit -> Objects.toString(hit.content(), "").length() + hit.rawContent().length())
			.sum();
	}

	private static String queryOf(ToolCall call) {
		try {
			JsonNode query = JSON.readTree(call.arguments()).get("query");
			return query == null || query.asString().isBlank() ? null : query.asString();
		}
		catch (JacksonException ex) {
			return null;
		}
	}
}
