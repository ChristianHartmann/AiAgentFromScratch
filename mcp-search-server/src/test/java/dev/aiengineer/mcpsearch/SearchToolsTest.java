package dev.aiengineer.mcpsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SearchToolsTest {

	private static final String SEARCH = "http://localhost:8888/search";

	private static final String RESPONSE = """
			{"query":"anything","results":[
			  {"title":"First","url":"https://example.com/1","content":"One","engine":"bing"},
			  {"title":"Second","url":"https://example.com/2","content":"Two","engine":"google"}
			]}
			""";

	private final RestClient.Builder restClient = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClient).build();

	private final SearchTools tools = new SearchTools(new SearxngSearchClient(restClient, "http://localhost:8888"));

	@Test
	void formatsEveryResultLikeTheBook() {
		server.expect(requestTo(startsWith(SEARCH)))
			.andExpect(queryParam("q", "anything"))
			.andExpect(queryParam("format", "json"))
			.andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

		String result = tools.searchWeb("anything", null);

		assertThat(result).isEqualTo("""
				Title: First
				URL: https://example.com/1
				Content: One

				Title: Second
				URL: https://example.com/2
				Content: Two""");
	}

	@Test
	void cutsTheResultsToTheRequestedNumber() {
		server.expect(requestTo(startsWith(SEARCH))).andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

		assertThat(tools.searchWeb("anything", 1)).contains("First").doesNotContain("Second");
	}

	@Test
	void returnsFailuresAsTextLikeTheBook() {
		server.expect(requestTo(startsWith(SEARCH))).andRespond(withServerError());

		assertThat(tools.searchWeb("anything", null)).startsWith("Error searching web:");
	}

	@Test
	void explainsWhatToDoWhenJsonIsDisabled() {
		server.expect(requestTo(startsWith(SEARCH))).andRespond(withStatus(HttpStatus.FORBIDDEN));

		assertThat(tools.searchWeb("anything", null)).startsWith("Error searching web:").contains("settings.yml");
	}
}
