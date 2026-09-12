package dev.aiengineer.agent.gaia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GaiaDatasetTest {

	private final RestClient.Builder restClient = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClient).build();

	@Test
	void readsTasksFromTheRowsApiResponse() throws IOException {
		List<GaiaProblem> tasks = GaiaDataset.parse(sampleResponse());

		assertThat(tasks).hasSize(4);
		assertThat(tasks.getFirst()).isEqualTo(new GaiaProblem(
			"11111111-1111-4111-8111-111111111111",
			"How many matchboxes fit side by side on a shelf 80 cm wide if one box is 5 cm wide?",
			1, "16", "", "1. A calculator"));
	}

	@Test
	void readsTheLevelAlthoughTheApiSendsItAsString() throws IOException {
		List<GaiaProblem> tasks = GaiaDataset.parse(sampleResponse());

		assertThat(tasks).extracting(GaiaProblem::level).containsExactly(1, 2, 1, 1);
	}

	@Test
	void recognizesTasksWithAnAttachment() throws IOException {
		List<GaiaProblem> tasks = GaiaDataset.parse(sampleResponse());

		assertThat(tasks).extracting(GaiaProblem::hasAttachment).containsExactly(false, true, false, false);
	}

	@Test
	void readsTheToolsTheAnnotatorsUsed() throws IOException {
		List<GaiaProblem> tasks = GaiaDataset.parse(sampleResponse());

		assertThat(tasks.getFirst().annotatorTools()).isEqualTo("1. A calculator");
		assertThat(tasks).extracting(GaiaProblem::needsWebSearch).containsExactly(false, false, false, true);
	}

	@Test
	void loadsOnlyTasksThatNeedAWebSearch() throws IOException {
		server.expect(requestTo(startsWith("https://datasets-server.huggingface.co/rows")))
			.andExpect(queryParam("length", "100"))
			.andRespond(withSuccess(sampleResponse(), MediaType.APPLICATION_JSON));

		List<GaiaProblem> tasks = dataset("test-token").loadNeedingWebSearch(10);

		assertThat(tasks).extracting(GaiaProblem::taskId).containsExactly("44444444-4444-4444-8444-444444444444");
	}

	@Test
	void loadsTheRequestedNumberOfTasksWithTheToken() throws IOException {
		server.expect(requestTo(startsWith("https://datasets-server.huggingface.co/rows")))
			.andExpect(method(HttpMethod.GET))
			.andExpect(queryParam("dataset", "gaia-benchmark/GAIA"))
			.andExpect(queryParam("config", "2023_level1"))
			.andExpect(queryParam("split", "validation"))
			.andExpect(queryParam("length", "3"))
			.andExpect(header("Authorization", "Bearer test-token"))
			.andRespond(withSuccess(sampleResponse(), MediaType.APPLICATION_JSON));

		List<GaiaProblem> tasks = dataset("test-token").load(3);

		assertThat(tasks).hasSize(4);
		server.verify();
	}

	@Test
	void reportsMissingAccessWithAClearMessage() {
		server.expect(requestTo(startsWith("https://datasets-server.huggingface.co/rows")))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> dataset("wrong-token").load(3))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("401")
			.hasMessageContaining("HF_TOKEN");
	}

	@Test
	void refusesToLoadWithoutAToken() {
		assertThatThrownBy(() -> dataset("").load(3))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("HF_TOKEN");
		server.verify();
	}

	@Test
	void rejectsCountsOutsideTheLimitsOfTheApi() {
		assertThatThrownBy(() -> dataset("test-token").load(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> dataset("test-token").load(101)).isInstanceOf(IllegalArgumentException.class);
	}

	private GaiaDataset dataset(String token) {
		return new GaiaDataset(restClient, new GaiaProperties(token, "2023_level1", 5, List.of()));
	}

	private static String sampleResponse() throws IOException {
		return new ClassPathResource("gaia/rows-example.json").getContentAsString(StandardCharsets.UTF_8);
	}
}
