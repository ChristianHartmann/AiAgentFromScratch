package dev.aiengineer.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.aiengineer.agent.llm.ToolDefinition;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class McpToolSourceTest {

	private final McpSchema.Tool webSearch = new McpSchema.Tool("searxng_web_search", null,
			"Performs a web search using the SearXNG API",
			Map.of("type", "object",
				"properties", Map.of("query", Map.of("type", "string", "description", "Search query")),
				"required", List.of("query")),
			null, null, null);

	@Test
	void takesNameAndDescriptionOfTheMcpTool() {
		ToolDefinition definition = McpToolSource.toDefinition(webSearch);

		assertThat(definition.name()).isEqualTo("searxng_web_search");
		assertThat(definition.description()).isEqualTo("Performs a web search using the SearXNG API");
	}

	@Test
	void takesTheInputSchemaAsItIs() {
		ToolDefinition definition = McpToolSource.toDefinition(webSearch);

		var schema = JsonMapper.shared().readTree(definition.inputSchema());
		assertThat(schema.path("properties").path("query").path("type").asString()).isEqualTo("string");
		assertThat(schema.path("required").get(0).asString()).isEqualTo("query");
	}

	@Test
	void usesAnEmptyDescriptionWhenTheServerSendsNone() {
		McpSchema.Tool withoutDescription = new McpSchema.Tool("ping", null, null, Map.of("type", "object"),
				null, null, null);

		assertThat(McpToolSource.toDefinition(withoutDescription).description()).isEmpty();
	}

	@Test
	void joinsTheTextContentsOfAResult() {
		McpSchema.CallToolResult result = new McpSchema.CallToolResult(
				List.of(new McpSchema.TextContent("first"), new McpSchema.TextContent("second")), false, null, null);

		assertThat(McpToolSource.toText(result)).isEqualTo("first\nsecond");
	}

	@Test
	void turnsAnErrorResultIntoAnException() {
		McpSchema.CallToolResult result = new McpSchema.CallToolResult(
				List.of(new McpSchema.TextContent("SearXNG is not reachable")), true, null, null);

		assertThatThrownBy(() -> McpToolSource.toText(result))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SearXNG is not reachable");
	}
}
