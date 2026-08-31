package dev.aiengineer.mcpsearch;

import java.util.stream.Collectors;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The tool of the custom MCP server from section 3.4.4. As in the book, failures come back as
 * text instead of an MCP error.
 */
@Component
public class SearchTools {

	private static final int DEFAULT_MAX_RESULTS = 5;

	private final SearxngSearchClient searxng;

	public SearchTools(SearxngSearchClient searxng) {
		this.searxng = searxng;
	}

	@McpTool(name = "searchWeb", description = "Search the web using SearXNG. Returns title, URL and content of each result.")
	public String searchWeb(@McpToolParam(description = "Search query string", required = true) String query,
			@McpToolParam(description = "Maximum number of results to return (default: 5)", required = false) Integer maxResults) {
		try {
			return searxng.search(query, maxResults == null ? DEFAULT_MAX_RESULTS : maxResults).stream()
				.map(result -> "Title: " + result.title() + "\nURL: " + result.url() + "\nContent: " + result.content())
				.collect(Collectors.joining("\n\n"));
		}
		catch (RuntimeException ex) {
			return "Error searching web: " + ex.getMessage();
		}
	}
}
