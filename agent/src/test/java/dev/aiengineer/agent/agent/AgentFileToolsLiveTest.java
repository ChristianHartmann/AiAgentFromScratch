package dev.aiengineer.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.aiengineer.agent.gaia.GaiaAgentEvaluator;
import dev.aiengineer.agent.gaia.GaiaAttachments;
import dev.aiengineer.agent.gaia.GaiaDataset;
import dev.aiengineer.agent.gaia.GaiaEvaluator;
import dev.aiengineer.agent.gaia.GaiaProblem;
import dev.aiengineer.agent.gaia.GaiaProperties;
import dev.aiengineer.agent.llm.LiveTests;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.ModelRouter;
import dev.aiengineer.agent.tool.FileTools;
import dev.aiengineer.agent.tool.FunctionTool;
import dev.aiengineer.agent.tool.MediaTools;
import dev.aiengineer.agent.tool.Tool;
import dev.aiengineer.agent.tool.WebSearchTools;
import dev.aiengineer.agent.tool.Workspace;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.RestClient;

/**
 * Section 5.4.4: the agent explores the zip attachment of a GAIA task with the file tools.
 * The book shows the run but no result. Question and answer come from the dataset at run
 * time and are never printed.
 */
@Tag("llm")
@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class AgentFileToolsLiveTest {

	private static final String MODEL = "google/gemini-3.6-flash";

	@Autowired
	private LlmClient llm;

	@Autowired
	private ModelRouter router;

	@Autowired
	private RestClient.Builder restClient;

	@Autowired
	private GaiaProperties properties;

	@Autowired
	private GaiaAttachments attachments;

	@Autowired
	private WebSearchTools webSearch;

	@Value("${agent.media.model}")
	private String mediaModel;

	@Test
	void solvesTheZipTaskOfTheBook() throws IOException {
		LiveTests.assumeKeyPresentFor(router, MODEL);
		assumeTrue(properties.hasToken(), "HF_TOKEN is not set");
		GaiaDataset level2 = new GaiaDataset(restClient,
				new GaiaProperties(properties.hfToken(), "2023_level2", 1, List.of()));
		GaiaProblem task = level2.load(100).stream()
			.filter(candidate -> candidate.taskId().startsWith("bfcd99e1"))
			.findFirst()
			.orElseThrow();
		Path workspace = attachments.prepareWorkspace(task);
		try {
			Workspace files = new Workspace(workspace);
			List<Tool> tools = new ArrayList<>(FunctionTool.allOf(webSearch));
			tools.addAll(FunctionTool.allOf(new FileTools(files)));
			tools.addAll(FunctionTool.allOf(new MediaTools(files, llm, mediaModel)));

			AgentResult<String> result = Agent.builder(llm)
				.model(MODEL)
				.instructions("You are a helpful assistant that can search the web and explore files to answer "
						+ "questions. Answer with the final answer only.")
				.tools(tools)
				.maxSteps(20)
				.build()
				.run(GaiaAgentEvaluator.prompt(task));

			assertThat(result.status())
				.withFailMessage(() -> "The run ended with " + result.status() + (result.error() == null ? ""
						: ": " + result.error().getClass().getSimpleName() + " " + result.error().getMessage()))
				.isEqualTo(AgentResult.Status.COMPLETE);
			assertThat(GaiaEvaluator.isCorrect(result.output(), task.finalAnswer()))
				.withFailMessage("The agent's answer does not match the dataset answer (neither is printed)")
				.isTrue();
		}
		finally {
			FileSystemUtils.deleteRecursively(workspace);
		}
	}
}
