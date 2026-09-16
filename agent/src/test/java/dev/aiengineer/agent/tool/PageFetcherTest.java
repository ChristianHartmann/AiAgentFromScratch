package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PageFetcherTest {

	private HttpServer server;

	private final PageFetcher fetcher = new PageFetcher(Duration.ofMillis(500));

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void returnsTheReadableTextOfAPage() {
		serve("/page", "text/html; charset=utf-8",
				"<html><head><title>T</title><script>var x = 1;</script></head>"
						+ "<body><h1>Nobel Prize</h1><p>Awarded for quantum circuits.</p></body></html>");

		assertThat(fetcher.text(url("/page"))).hasValue("Nobel Prize Awarded for quantum circuits.");
	}

	@Test
	void cutsVeryLongPages() {
		serve("/long", "text/html", "<html><body><p>" + "word ".repeat(20_000) + "</p></body></html>");

		assertThat(fetcher.text(url("/long"))).hasValueSatisfying(
				text -> assertThat(text).hasSize(PageFetcher.MAX_CHARS));
	}

	@Test
	void ignoresContentThatIsNotHtml() {
		serve("/file.pdf", "application/pdf", "%PDF-1.4 binary");

		assertThat(fetcher.text(url("/file.pdf"))).isEmpty();
	}

	@Test
	void ignoresPagesThatAnswerWithAnError() {
		server.createContext("/missing", exchange -> {
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
		});

		assertThat(fetcher.text(url("/missing"))).isEmpty();
	}

	@Test
	void givesUpOnPagesThatTakeTooLong() {
		server.createContext("/slow", exchange -> {
			try {
				Thread.sleep(2_000);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			exchange.close();
		});

		assertThat(fetcher.text(url("/slow"))).isEmpty();
	}

	@Test
	void loadsOnlyHttpAndHttps() {
		assertThat(fetcher.text("file:///etc/passwd")).isEmpty();
		assertThat(fetcher.text("ftp://example.com/page")).isEmpty();
		assertThat(fetcher.text("not a url")).isEmpty();
	}

	private void serve(String path, String contentType, String body) {
		server.createContext(path, exchange -> {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", contentType);
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
	}

	private String url(String path) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + path;
	}
}
