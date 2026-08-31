package dev.aiengineer.mcpsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the JSON API of a SearXNG instance. The agent module has its own small client on
 * purpose: one REST call does not justify a shared module.
 */
@Component
public class SearxngSearchClient {

	private final RestClient restClient;

	public SearxngSearchClient(RestClient.Builder restClient, @Value("${searxng.url}") String url) {
		this.restClient = restClient.baseUrl(url).build();
	}

	public List<Result> search(String query, int maxResults) {
		return restClient.get()
			.uri(uri -> uri.path("/search").queryParam("q", query).queryParam("format", "json").build())
			.retrieve()
			.onStatus(status -> status.value() == 403, (request, response) -> {
				throw new IllegalStateException("SearXNG refused JSON output (HTTP 403), "
						+ "enable json under search.formats in searxng/settings.yml");
			})
			.body(Response.class)
			.results()
			.stream()
			.limit(maxResults)
			.toList();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Result(String title, String url, String content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Response(List<Result> results) {
	}
}
