package dev.aiengineer.agent.gaia;

import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads GAIA tasks through the rows API of the Hugging Face dataset viewer. The dataset is
 * gated, so every request needs a token of an account that accepted its terms.
 */
@Component
public class GaiaDataset {

	static final String ROWS_API = "https://datasets-server.huggingface.co/rows";

	private static final String DATASET = "gaia-benchmark/GAIA";

	private static final int MAX_ROWS_PER_REQUEST = 100;

	private final RestClient restClient;

	private final GaiaProperties properties;

	public GaiaDataset(RestClient.Builder restClient, GaiaProperties properties) {
		this.restClient = restClient.baseUrl(ROWS_API).build();
		this.properties = properties;
	}

	/**
	 * Loads the first {@code count} tasks of the validation split.
	 */
	public List<GaiaProblem> load(int count) {
		if (count < 1 || count > MAX_ROWS_PER_REQUEST) {
			throw new IllegalArgumentException(
				"count must be between 1 and " + MAX_ROWS_PER_REQUEST + ", was " + count);
		}
		if (!properties.hasToken()) {
			throw new IllegalStateException("HF_TOKEN is not set, the GAIA dataset is gated");
		}
		String body = restClient.get()
			.uri(uri -> uri
				.queryParam("dataset", DATASET)
				.queryParam("config", properties.split())
				.queryParam("split", "validation")
				.queryParam("offset", 0)
				.queryParam("length", count)
				.build())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.hfToken())
			.retrieve()
			.onStatus(HttpStatusCode::isError, (request, response) -> {
				throw new IllegalStateException("GAIA dataset not reachable (HTTP " + response.getStatusCode().value()
						+ "). Check HF_TOKEN and that the account accepted the terms of " + DATASET);
			})
			.body(String.class);
		return parse(body);
	}

	static List<GaiaProblem> parse(String json) {
		JsonNode rows = JsonMapper.shared().readTree(json).path("rows");
		return StreamSupport.stream(rows.spliterator(), false)
			.map(row -> row.path("row"))
			.map(GaiaDataset::toProblem)
			.toList();
	}

	private static GaiaProblem toProblem(JsonNode row) {
		return new GaiaProblem(
			row.path("task_id").asString(),
			row.path("Question").asString(),
			Integer.parseInt(row.path("Level").asString()),
			row.path("Final answer").asString(),
			row.path("file_name").asString(""));
	}
}
