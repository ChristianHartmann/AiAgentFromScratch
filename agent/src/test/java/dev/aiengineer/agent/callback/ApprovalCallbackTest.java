package dev.aiengineer.agent.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.agent.Agent;
import dev.aiengineer.agent.agent.AgentResult;
import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ContentItem;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.LlmResponse;
import dev.aiengineer.agent.llm.Message;
import dev.aiengineer.agent.llm.Role;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolResult;
import dev.aiengineer.agent.llm.Usage;
import dev.aiengineer.agent.tool.DeleteFileTools;
import dev.aiengineer.agent.tool.FileTools;
import dev.aiengineer.agent.tool.FunctionTool;
import dev.aiengineer.agent.tool.Tool;
import dev.aiengineer.agent.tool.Workspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovalCallbackTest {

	@TempDir
	Path root;

	private final List<String> asked = new ArrayList<>();

	private List<Tool> tools;

	private final ToolCall delete = new ToolCall("call_1", "deleteFile", "{\"filePath\":\"report.txt\"}");

	@BeforeEach
	void createTools() {
		Workspace workspace = new Workspace(root);
		tools = new ArrayList<>(FunctionTool.allOf(new FileTools(workspace)));
		tools.addAll(FunctionTool.allOf(new DeleteFileTools(workspace)));
	}

	@Test
	void letsAnApprovedToolRun() {
		assertThat(callback(true).beforeTool(new ExecutionContext(), delete)).isEmpty();
		assertThat(asked).containsExactly("deleteFile {\"filePath\":\"report.txt\"}");
	}

	@Test
	void answersForADeniedToolWithAnError() {
		Optional<ToolResult> result = callback(false).beforeTool(new ExecutionContext(), delete);

		assertThat(result).contains(ToolResult.error(delete, "User denied execution of deleteFile"));
	}

	@Test
	void asksOnlyForToolsThatRequireConfirmation() {
		ToolCall list = new ToolCall("call_2", "listFiles", "{}");

		assertThat(callback(false).beforeTool(new ExecutionContext(), list)).isEmpty();
		assertThat(asked).isEmpty();
	}

	@Test
	void leavesUnknownToolsToTheAgent() {
		assertThat(callback(false).beforeTool(new ExecutionContext(), new ToolCall("c", "teleport", "{}"))).isEmpty();
	}

	@Test
	void keepsADeniedToolFromRunningInAnAgent() {
		LlmClient llm = mock(LlmClient.class);
		List<ContentItem> answer = new ArrayList<>();
		answer.add(new Message(Role.ASSISTANT, ""));
		answer.add(delete);
		when(llm.generate(any())).thenReturn(new LlmResponse(answer, Usage.NONE))
			.thenReturn(new LlmResponse(List.of(new Message(Role.ASSISTANT, "Not deleted")), Usage.NONE));

		AgentResult<String> result = Agent.builder(llm)
			.model("google/gemini-3.6-flash")
			.tools(tools)
			.beforeToolCallbacks(List.of(callback(false)))
			.build()
			.run("Delete report.txt");

		assertThat(result.context().events().get(2).content())
			.containsExactly(ToolResult.error(delete, "User denied execution of deleteFile"));
	}

	private ApprovalCallback callback(boolean approve) {
		return new ApprovalCallback((toolName, arguments) -> {
			asked.add(toolName + " " + arguments);
			return approve;
		}, tools);
	}
}
