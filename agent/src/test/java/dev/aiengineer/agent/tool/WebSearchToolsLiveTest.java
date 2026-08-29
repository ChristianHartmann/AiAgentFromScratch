package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.aiengineer.agent.llm.LiveTests;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("llm")
@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class WebSearchToolsLiveTest {

	@Autowired
	private WebSearchTools webSearch;

	@Test
	void findsResultsForARealQuery() {
		LiveTests.assumeSearxngRunning();

		assertThat(webSearch.searchWeb("Eliud Kipchoge marathon world record", 3, null, null))
			.isNotEmpty()
			.hasSizeLessThanOrEqualTo(3)
			.allSatisfy(result -> assertThat(result.url()).startsWith("http"));
	}
}
