package dev.aiengineer.agent.llm;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Replaces the Google GenAI client of the Spring AI auto-configuration, which waits forever
 * for an answer: Spring AI 2.0.1 offers timeouts for OpenAI and Anthropic, but none for
 * Google. A hanging call would block the caller and keep a slot of the provider's semaphore.
 *
 * <p>The timeout applies to each attempt. The client retries failed attempts with growing
 * pauses, so a caller sees the failure after a multiple of it: about 13 seconds for a timeout
 * of one second, measured against a server that never answers.
 *
 * <p>Supports the Gemini Developer API with an API key, not Vertex AI.
 */
@Configuration(proxyBeanMethods = false)
public class GoogleGenAiClientConfiguration {

	@Bean
	Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey,
			@Value("${agent.llm.google.timeout:60s}") Duration timeout) {
		return client(apiKey, timeout, null);
	}

	static Client client(String apiKey, Duration timeout, String baseUrl) {
		HttpOptions.Builder options = HttpOptions.builder().timeout(Math.toIntExact(timeout.toMillis()));
		if (baseUrl != null) {
			options.baseUrl(baseUrl);
		}
		return Client.builder().apiKey(apiKey).httpOptions(options.build()).build();
	}
}
