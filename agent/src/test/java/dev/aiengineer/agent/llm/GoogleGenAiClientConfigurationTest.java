package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.Client;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class GoogleGenAiClientConfigurationTest {

	private HttpServer silentServer;

	/**
	 * Accepts every request and never answers, like the Gemini call that hung the loop test.
	 */
	@BeforeEach
	void startSilentServer() throws IOException {
		silentServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		silentServer.createContext("/", exchange -> {
			try {
				Thread.sleep(Duration.ofMinutes(5));
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			exchange.close();
		});
		silentServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		silentServer.start();
	}

	@AfterEach
	void stopSilentServer() {
		silentServer.stop(0);
	}

	/**
	 * The client retries after a timeout with growing pauses, so the failure arrives after a
	 * multiple of the timeout. What matters is that it arrives at all: without a timeout the
	 * call waits until the server closes the connection.
	 */
	@Test
	@Timeout(60)
	void givesUpWhenTheServerDoesNotAnswer() {
		Client client = GoogleGenAiClientConfiguration.client("test-key", Duration.ofSeconds(1),
				"http://127.0.0.1:" + silentServer.getAddress().getPort());
		long start = System.nanoTime();

		assertThatThrownBy(() -> client.models.generateContent("gemini-3.6-flash", "Hi", null))
			.isInstanceOf(RuntimeException.class);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(30));
	}
}
