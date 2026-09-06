package dev.aiengineer.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.LiveTests;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.ModelRouter;
import dev.aiengineer.agent.loop.SimpleAgentLoop;
import dev.aiengineer.agent.tool.Tool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class McpToolSourceLiveTest {

	private static final String MODEL = "google/gemini-3.6-flash";

	@Autowired
	private LlmClient llm;

	@Autowired
	private ModelRouter router;

	@Test
	@Tag("external")
	void listsAndCallsTheToolsOfTheSearxngServer() throws Exception {
		String searxngUrl = LiveTests.assumeSearxngRunning();

		try (McpToolSource searxng = searxng(searxngUrl)) {
			List<Tool> tools = searxng.tools();
			assertThat(tools).extracting(Tool::name).contains("searxng_web_search");

			Tool search = tools.stream().filter(tool -> tool.name().equals("searxng_web_search"))
				.findFirst().orElseThrow();
			Object result = search.execute(new ExecutionContext(), "{\"query\":\"Eliud Kipchoge marathon world record\"}");

			assertThat(result).asString().containsIgnoringCase("Kipchoge");
		}
	}

	@Test
	@Tag("llm")
	void letsTheLoopSearchThroughMcp() {
		LiveTests.assumeKeyPresentFor(router, MODEL);
		String searxngUrl = LiveTests.assumeSearxngRunning();

		try (McpToolSource searxng = searxng(searxngUrl)) {
			String answer = new SimpleAgentLoop(llm).run(MODEL,
					"You are a helpful assistant. Always search the web before answering.",
					"Who won the 2025 Nobel Prize in Physics?", searxng.tools());

			assertThat(answer).containsAnyOf("Clarke", "Devoret", "Martinis");
		}
	}

	private static McpToolSource searxng(String searxngUrl) {
		return McpToolSource.start("npx", List.of("-y", "mcp-searxng@2.4.0"), Map.of("SEARXNG_URL", searxngUrl));
	}
}
