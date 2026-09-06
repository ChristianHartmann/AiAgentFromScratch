package dev.aiengineer.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.LiveTests;
import dev.aiengineer.agent.tool.Tool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("external")
class McpSearchServerLiveTest {

	@Test
	void offersAndRunsTheSearchToolOfTheOwnServer() throws Exception {
		String searxngUrl = LiveTests.assumeSearxngRunning();
		String jar = System.getProperty("mcpSearchServerJar");

		try (McpToolSource server = McpToolSource.start("java", List.of("-jar", jar),
				Map.of("SEARXNG_URL", searxngUrl))) {
			List<Tool> tools = server.tools();
			assertThat(tools).singleElement().satisfies(tool -> {
				assertThat(tool.name()).isEqualTo("searchWeb");
				assertThat(tool.description()).contains("SearXNG");
				assertThat(tool.definition().inputSchema()).contains("query").contains("maxResults");
			});

			Object result = tools.getFirst().execute(new ExecutionContext(), "{\"query\":\"Eliud Kipchoge\",\"maxResults\":2}");

			assertThat(result).asString().startsWith("Title:").contains("URL:");
		}
	}
}
