package dev.aiengineer.agent.tool;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/**
 * Loads a web page and returns its readable text, for search results with page content
 * (section 5.3.5). The book gets the content from Tavily's include_raw_content; SearXNG has
 * only snippets. Every failure ends in an empty result: a page that does not load must not
 * break the search.
 */
@Component
public class PageFetcher {

	static final int MAX_CHARS = 50_000;

	private static final int MAX_BYTES = 2_000_000;

	private static final String USER_AGENT = "Mozilla/5.0 (compatible; aiengineer-agent/0.1)";

	private final Duration timeout;

	private final HttpClient http;

	public PageFetcher() {
		this(Duration.ofSeconds(10));
	}

	PageFetcher(Duration timeout) {
		this.timeout = timeout;
		this.http = HttpClient.newBuilder()
			.connectTimeout(timeout)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	}

	/**
	 * The text of an HTML page, at most 50,000 characters; empty for anything that is not
	 * http or https, not HTML, not 200, or slower than the timeout.
	 */
	public Optional<String> text(String url) {
		URI uri;
		try {
			uri = URI.create(url);
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
		if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
			return Optional.empty();
		}
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", USER_AGENT).GET().build();
		try {
			HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
				if (response.statusCode() != 200 || !contentType.contains("text/html")) {
					return Optional.empty();
				}
				byte[] html = body.readNBytes(MAX_BYTES);
				String text = Jsoup.parse(new ByteArrayInputStream(html), null, url).body().text();
				return text.isBlank() ? Optional.empty() : Optional.of(text.substring(0, Math.min(text.length(), MAX_CHARS)));
			}
		}
		catch (IOException ex) {
			return Optional.empty();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}
}
