package dev.aiengineer.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.aiengineer.agent.llm.LiveTests;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.tool.Toolbox;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("llm")
class McpSearchServerLiveTest {

	@Test
	void offersAndRunsTheSearchToolOfTheOwnServer() {
		String searxngUrl = LiveTests.assumeSearxngRunning();
		String jar = System.getProperty("mcpSearchServerJar");

		try (McpToolSource server = McpToolSource.start("java", List.of("-jar", jar),
				Map.of("SEARXNG_URL", searxngUrl))) {
			assertThat(server.tools()).singleElement().satisfies(tool -> {
				assertThat(tool.name()).isEqualTo("searchWeb");
				assertThat(tool.description()).contains("SearXNG");
				assertThat(tool.inputSchema()).contains("query").contains("maxResults");
			});

			String result = server.registerInto(new Toolbox())
				.execute(new ToolCall("1", "searchWeb", "{\"query\":\"Eliud Kipchoge\",\"maxResults\":2}"));

			assertThat(result).startsWith("Title:").contains("URL:");
		}
	}
}
