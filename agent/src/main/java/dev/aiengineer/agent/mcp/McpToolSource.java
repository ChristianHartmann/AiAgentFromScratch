package dev.aiengineer.agent.mcp;

import dev.aiengineer.agent.llm.ToolDefinition;
import dev.aiengineer.agent.tool.Toolbox;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tools of an MCP server, started as subprocess and spoken to over stdio. Follows the client
 * of section 3.4.3: initialize, list the tools, turn them into tool definitions, call them.
 */
public final class McpToolSource implements AutoCloseable {

	private static final JsonMapper JSON = JsonMapper.shared();

	private final McpSyncClient client;

	private McpToolSource(McpSyncClient client) {
		this.client = client;
	}

	/**
	 * Starts the server and completes the MCP handshake. The first start of an npx based
	 * server downloads it, hence the generous initialization timeout.
	 */
	public static McpToolSource start(String command, List<String> args, Map<String, String> env) {
		ServerParameters parameters = ServerParameters.builder(command).args(args).env(env).build();
		McpSyncClient client = McpClient.sync(new StdioClientTransport(parameters, new JacksonMcpJsonMapper(JSON)))
			.requestTimeout(Duration.ofSeconds(60))
			.initializationTimeout(Duration.ofSeconds(120))
			.build();
		client.initialize();
		return new McpToolSource(client);
	}

	public List<ToolDefinition> tools() {
		return mcpTools().stream().map(McpToolSource::toDefinition).toList();
	}

	/**
	 * Adds every tool of the server to the toolbox, executed by calling the server.
	 */
	public Toolbox registerInto(Toolbox toolbox) {
		for (McpSchema.Tool tool : mcpTools()) {
			toolbox.add(toDefinition(tool), arguments -> toText(
					client.callTool(new McpSchema.CallToolRequest(tool.name(), parse(arguments)))));
		}
		return toolbox;
	}

	@Override
	public void close() {
		client.closeGracefully();
	}

	static ToolDefinition toDefinition(McpSchema.Tool tool) {
		String description = tool.description() == null ? "" : tool.description();
		return new ToolDefinition(tool.name(), description, JSON.writeValueAsString(tool.inputSchema()));
	}

	static String toText(McpSchema.CallToolResult result) {
		String text = result.content().stream()
			.filter(McpSchema.TextContent.class::isInstance)
			.map(content -> ((McpSchema.TextContent) content).text())
			.collect(Collectors.joining("\n"));
		if (Boolean.TRUE.equals(result.isError())) {
			throw new IllegalStateException(text);
		}
		return text;
	}

	private List<McpSchema.Tool> mcpTools() {
		List<McpSchema.Tool> tools = new ArrayList<>();
		McpSchema.ListToolsResult page = client.listTools();
		tools.addAll(page.tools());
		while (page.nextCursor() != null) {
			page = client.listTools(page.nextCursor());
			tools.addAll(page.tools());
		}
		return tools;
	}

	private static Map<String, Object> parse(String arguments) {
		return JSON.readValue(arguments == null || arguments.isBlank() ? "{}" : arguments,
				new TypeReference<Map<String, Object>>() {
				});
	}
}
