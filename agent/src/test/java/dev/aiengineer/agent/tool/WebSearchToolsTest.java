package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.aiengineer.agent.tool.WebSearchTools.Category;
import dev.aiengineer.agent.tool.WebSearchTools.SearchResult;
import dev.aiengineer.agent.tool.WebSearchTools.TimeRange;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class WebSearchToolsTest {

	private static final String SEARCH = "http://localhost:8888/search";

	private final RestClient.Builder restClient = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClient).build();

	private final PageFetcher pageFetcher = mock(PageFetcher.class);

	private final WebSearchTools webSearch = new WebSearchTools(restClient,
			new SearxngProperties("http://localhost:8888"), pageFetcher);

	@Test
	void asksForJson() {
		server.expect(requestTo(startsWith(SEARCH)))
			.andExpect(method(HttpMethod.GET))
			.andExpect(queryParam("q", "Kipchoge"))
			.andExpect(queryParam("format", "json"))
			.andExpect(request -> assertThat(request.getURI().getQuery())
				.doesNotContain("categories").doesNotContain("time_range"))
			.andRespond(withSuccess(response(1), MediaType.APPLICATION_JSON));

		webSearch.searchWeb("Kipchoge", null, null, null, null);

		server.verify();
	}

	@Test
	void passesFiltersInLowerCase() {
		server.expect(requestTo(startsWith(SEARCH)))
			.andExpect(queryParam("categories", "news"))
			.andExpect(queryParam("time_range", "week"))
			.andRespond(withSuccess(response(1), MediaType.APPLICATION_JSON));

		webSearch.searchWeb("Kipchoge", null, Category.NEWS, TimeRange.WEEK, null);

		server.verify();
	}

	@Test
	void returnsTitleUrlAndContentOfEveryResult() {
		server.expect(requestTo(startsWith(SEARCH))).andRespond(withSuccess(response(1), MediaType.APPLICATION_JSON));

		List<SearchResult> results = webSearch.searchWeb("Kipchoge", null, null, null, null);

		assertThat(results).containsExactly(new SearchResult("Title 0", "https://example.com/0", "Content 0"));
	}

	@Test
	void returnsFiveResultsUnlessToldOtherwise() {
		server.expect(requestTo(startsWith(SEARCH))).andRespond(withSuccess(response(7), MediaType.APPLICATION_JSON));

		assertThat(webSearch.searchWeb("Kipchoge", null, null, null, null)).hasSize(5);
	}

	@Test
	void cutsTheResultsToTheRequestedNumber() {
		server.expect(requestTo(startsWith(SEARCH))).andRespond(withSuccess(response(7), MediaType.APPLICATION_JSON));

		assertThat(webSearch.searchWeb("Kipchoge", 2, null, null, null)).extracting(SearchResult::title)
			.containsExactly("Title 0", "Title 1");
	}

	@Test
	void explainsWhatToDoWhenJsonIsDisabled() {
		server.expect(requestTo(startsWith(SEARCH))).andRespond(withStatus(HttpStatus.FORBIDDEN));

		assertThatThrownBy(() -> webSearch.searchWeb("Kipchoge", null, null, null, null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("403")
			.hasMessageContaining("settings.yml");
	}

	@Test
	void loadsThePageOfEveryResultOnRequest() {
		server.expect(requestTo(startsWith(SEARCH))).andRespond(withSuccess(response(2), MediaType.APPLICATION_JSON));
		when(pageFetcher.text("https://example.com/0")).thenReturn(Optional.of("Full page 0"));
		when(pageFetcher.text("https://example.com/1")).thenReturn(Optional.of("Full page 1"));

		List<SearchResult> results = webSearch.searchWeb("Kipchoge", null, null, null, true);

		assertThat(results).extracting(SearchResult::rawContent).containsExactly("Full page 0", "Full page 1");
		assertThat(results).extracting(SearchResult::content).containsExactly("Content 0", "Content 1");
	}

	@Test
	void keepsTheSnippetWhenAPageCannotBeLoaded() {
		server.expect(requestTo(startsWith(SEARCH))).andRespond(withSuccess(response(1), MediaType.APPLICATION_JSON));
		when(pageFetcher.text("https://example.com/0")).thenReturn(Optional.empty());

		assertThat(webSearch.searchWeb("Kipchoge", null, null, null, true))
			.containsExactly(new SearchResult("Title 0", "https://example.com/0", "Content 0"));
	}

	@Test
	void loadsNoPagesUnlessAsked() {
		server.expect(requestTo(startsWith(SEARCH))).andRespond(withSuccess(response(1), MediaType.APPLICATION_JSON));

		webSearch.searchWeb("Kipchoge", null, null, null, null);

		verifyNoInteractions(pageFetcher);
	}

	@Test
	void leavesAnEmptyPageContentOutOfTheJson() {
		String json = JsonMapper.shared()
			.writeValueAsString(new SearchResult("Title", "https://example.com", "Snippet"));

		assertThat(json).doesNotContain("rawContent");
	}

	/**
	 * A response of the SearXNG JSON API, reduced to the fields we read plus a few we ignore.
	 */
	private static String response(int count) {
		String results = IntStream.range(0, count)
			.mapToObj(i -> """
					{"title":"Title %d","url":"https://example.com/%d","content":"Content %d",
					 "engine":"bing","score":1.0,"category":"general"}""".formatted(i, i, i))
			.collect(Collectors.joining(","));
		return """
				{"query":"Kipchoge","results":[%s],"answers":[],"suggestions":[],
				 "unresponsive_engines":[["duckduckgo","CAPTCHA"]]}""".formatted(results);
	}
}
