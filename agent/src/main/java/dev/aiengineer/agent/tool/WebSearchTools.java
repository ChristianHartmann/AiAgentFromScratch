package dev.aiengineer.agent.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Web search through a local SearXNG instance, the first real tool of section 3.3.1. The
 * book uses Tavily; SearXNG needs no key and has no quota, see the spec for the trade-offs.
 */
@Component
public class WebSearchTools {

	public static final String SEARCH_WEB = "searchWeb";

	private static final int DEFAULT_MAX_RESULTS = 5;

	public enum Category {
		GENERAL, NEWS, SCIENCE
	}

	public enum TimeRange {
		DAY, WEEK, MONTH, YEAR
	}

	private final RestClient restClient;

	private final PageFetcher pageFetcher;

	/**
	 * A result of the search. rawContent holds the text of the page when it was requested and
	 * could be loaded, otherwise it is empty and left out of the JSON the model sees.
	 */
	public record SearchResult(String title, String url, String content,
			@JsonInclude(JsonInclude.Include.NON_EMPTY) String rawContent) {

		public SearchResult {
			rawContent = rawContent == null ? "" : rawContent;
		}

		public SearchResult(String title, String url, String content) {
			this(title, url, content, "");
		}
	}

	public WebSearchTools(RestClient.Builder restClient, SearxngProperties properties, PageFetcher pageFetcher) {
		this.restClient = restClient.baseUrl(properties.url()).build();
		this.pageFetcher = pageFetcher;
	}

	@ToolFunction("Search the web for current information. Returns title, URL and a content snippet per result, "
			+ "and the text of every result page if includeContent is true.")
	public List<SearchResult> searchWeb(@ToolParam("The search query") String query,
			@ToolParam(value = "Maximum number of results, 5 if omitted", required = false) Integer maxResults,
			@ToolParam(value = "Category of the search, general if omitted", required = false) Category category,
			@ToolParam(value = "Only results from this time range", required = false) TimeRange timeRange,
			@ToolParam(value = "Also load the text of every result page, for questions the snippets cannot answer",
					required = false) Boolean includeContent) {
		SearxngResponse response = restClient.get()
			.uri(uri -> {
				uri.path("/search").queryParam("q", query).queryParam("format", "json");
				if (category != null) {
					uri.queryParam("categories", category.name().toLowerCase(Locale.ROOT));
				}
				if (timeRange != null) {
					uri.queryParam("time_range", timeRange.name().toLowerCase(Locale.ROOT));
				}
				return uri.build();
			})
			.retrieve()
			.onStatus(status -> status.value() == 403, (request, answer) -> {
				throw new IllegalStateException("SearXNG refused JSON output (HTTP 403), "
						+ "enable json under search.formats in searxng/settings.yml");
			})
			.body(SearxngResponse.class);
		List<SearchResult> results = response.results().stream()
			.limit(maxResults == null ? DEFAULT_MAX_RESULTS : maxResults)
			.map(result -> new SearchResult(result.title(), result.url(), result.content()))
			.toList();
		return Boolean.TRUE.equals(includeContent) ? withPageContent(results) : results;
	}

	/**
	 * Loads the pages concurrently on virtual threads; each page has its own timeout, so the
	 * slowest page bounds the wait.
	 */
	private List<SearchResult> withPageContent(List<SearchResult> results) {
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<SearchResult>> futures = results.stream()
				.map(result -> executor.submit(() -> new SearchResult(result.title(), result.url(), result.content(),
						pageFetcher.text(result.url()).orElse(""))))
				.toList();
			return futures.stream().map(WebSearchTools::join).toList();
		}
	}

	private static SearchResult join(Future<SearchResult> future) {
		try {
			return future.get();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while loading pages", ex);
		}
		catch (ExecutionException ex) {
			throw new IllegalStateException("Loading a page failed", ex.getCause());
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SearxngResponse(List<SearxngResult> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SearxngResult(String title, String url, String content) {
	}
}
