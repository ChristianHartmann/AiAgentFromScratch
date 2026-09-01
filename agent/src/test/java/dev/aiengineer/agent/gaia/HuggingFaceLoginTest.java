package dev.aiengineer.agent.gaia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("external")
class HuggingFaceLoginTest {

	@Test
	void assureThatLoginWorksAndReturnsTasks() throws Exception {
		String token = System.getenv("HF_TOKEN");
		assumeTrue(token != null && !token.isBlank(), "HF_TOKEN is not set");

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("https://datasets-server.huggingface.co/rows"
				+ "?dataset=gaia-benchmark%2FGAIA&config=2023_level1&split=validation&offset=0&length=3"))
			.header("Authorization", "Bearer " + token)
			.GET()
			.build();

		HttpResponse<String> response = HttpClient.newHttpClient()
			.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"Question\"").contains("\"Final answer\"");
	}
}
