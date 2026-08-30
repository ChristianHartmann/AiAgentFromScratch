package dev.aiengineer.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.aiengineer.agent.llm.LiveTests;
import dev.aiengineer.agent.llm.LlmService;
import dev.aiengineer.agent.llm.ModelRouter;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolDefinition;
import dev.aiengineer.agent.loop.SimpleAgentLoop;
import dev.aiengineer.agent.tool.Toolbox;
import java.util.List;
import java.util.Map;
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
class McpToolSourceLiveTest {

	private static final String MODEL = "google/gemini-3.6-flash";

	@Autowired
	private LlmService llm;

	@Autowired
	private ModelRouter router;

	@Test
	void listsAndCallsTheToolsOfTheSearxngServer() {
		String searxngUrl = LiveTests.assumeSearxngRunning();

		try (McpToolSource searxng = searxng(searxngUrl)) {
			assertThat(searxng.tools()).extracting(ToolDefinition::name).contains("searxng_web_search");

			String result = searxng.registerInto(new Toolbox()).execute(
					new ToolCall("1", "searxng_web_search", "{\"query\":\"Eliud Kipchoge marathon world record\"}"));

			assertThat(result).doesNotStartWith("Error").containsIgnoringCase("Kipchoge");
		}
	}

	@Test
	void letsTheLoopSearchThroughMcp() {
		LiveTests.assumeKeyPresentFor(router, MODEL);
		String searxngUrl = LiveTests.assumeSearxngRunning();

		try (McpToolSource searxng = searxng(searxngUrl)) {
			String answer = new SimpleAgentLoop(llm).run(MODEL,
					"You are a helpful assistant. Always search the web before answering.",
					"Who won the 2025 Nobel Prize in Physics?", searxng.registerInto(new Toolbox()));

			assertThat(answer).containsAnyOf("Clarke", "Devoret", "Martinis");
		}
	}

	private static McpToolSource searxng(String searxngUrl) {
		return McpToolSource.start("npx", List.of("-y", "mcp-searxng@2.4.0"), Map.of("SEARXNG_URL", searxngUrl));
	}
}
