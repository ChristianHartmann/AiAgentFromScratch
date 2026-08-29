package dev.aiengineer.agent.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Web search through a local SearXNG instance, the first real tool of section 3.3.1. The
 * book uses Tavily; SearXNG needs no key and has no quota, see the spec for the trade-offs.
 */
@Component
public class WebSearchTools {

	private static final int DEFAULT_MAX_RESULTS = 5;

	private final RestClient restClient;

	public enum Category {
		GENERAL, NEWS, SCIENCE
	}

	public enum TimeRange {
		DAY, WEEK, MONTH, YEAR
	}

	public record SearchResult(String title, String url, String content) {
	}

	public WebSearchTools(RestClient.Builder restClient, SearxngProperties properties) {
		this.restClient = restClient.baseUrl(properties.url()).build();
	}

	@ToolFunction("Search the web for current information. Returns title, URL and a content snippet per result.")
	public List<SearchResult> searchWeb(@ToolParam("The search query") String query,
			@ToolParam(value = "Maximum number of results, 5 if omitted", required = false) Integer maxResults,
			@ToolParam(value = "Category of the search, general if omitted", required = false) Category category,
			@ToolParam(value = "Only results from this time range", required = false) TimeRange timeRange) {
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
		return response.results().stream()
			.limit(maxResults == null ? DEFAULT_MAX_RESULTS : maxResults)
			.map(result -> new SearchResult(result.title(), result.url(), result.content()))
			.toList();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SearxngResponse(List<SearxngResult> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SearxngResult(String title, String url, String content) {
	}
}
