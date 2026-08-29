package dev.aiengineer.agent.llm;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Helpers for tests that call real providers.
 */
public final class LiveTests {

	private LiveTests() {
	}

	public static void assumeKeyPresentFor(ModelRouter router, String model) {
		String variable = switch (router.provider(model)) {
			case OPENAI -> "OPENAI_API_KEY";
			case ANTHROPIC -> "ANTHROPIC_API_KEY";
			case GOOGLE -> "GEMINI_API_KEY";
		};
		assumeEnvironmentVariable(variable);
	}

	public static String assumeEnvironmentVariable(String variable) {
		String value = System.getenv(variable);
		assumeTrue(value != null && !value.isBlank(), variable + " is not set");
		return value;
	}

	/**
	 * Skips the test unless SearXNG answers, and returns its URL.
	 */
	public static String assumeSearxngRunning() {
		String url = System.getenv().getOrDefault("SEARXNG_URL", "http://localhost:8888");
		assumeTrue(isHealthy(url), "SearXNG is not running at " + url + ", start it with: docker compose up -d");
		return url;
	}

	private static boolean isHealthy(String url) {
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/healthz")).timeout(Duration.ofSeconds(3)).build();
		try {
			return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
		}
		catch (IOException ex) {
			return false;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
