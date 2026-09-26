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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
	 * http or https, not HTML, not 200, or not complete within the timeout. The timeout covers
	 * the whole page: the request timeout of the HTTP client ends with the headers, and a page
	 * that stalls in the middle of its body would otherwise block the search.
	 */
	public Optional<String> text(String url) {
		HttpRequest request = requestFor(url);
		if (request == null) {
			return Optional.empty();
		}
		FutureTask<Optional<String>> fetch = new FutureTask<>(() -> fetch(request, url));
		Thread.ofVirtual().start(fetch);
		try {
			return fetch.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException | ExecutionException ex) {
			fetch.cancel(true);
			return Optional.empty();
		}
		catch (InterruptedException ex) {
			fetch.cancel(true);
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}

	/**
	 * A request for http and https URLs the client can send, otherwise null. Search results
	 * contain URLs the client rejects, such as hosts with an underscore.
	 */
	private HttpRequest requestFor(String url) {
		if (url == null) {
			return null;
		}
		try {
			URI uri = URI.create(url);
			if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
				return null;
			}
			return HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", USER_AGENT).GET().build();
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/**
	 * Runs on its own virtual thread; cancelling interrupts it, which ends a blocked read of
	 * the body.
	 */
	private Optional<String> fetch(HttpRequest request, String url) throws IOException, InterruptedException {
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
}
