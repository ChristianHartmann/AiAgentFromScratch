package dev.aiengineer.agent.gaia;

import dev.aiengineer.agent.agent.Agent;
import dev.aiengineer.agent.agent.AgentResult;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.tool.FileTools;
import dev.aiengineer.agent.tool.FunctionTool;
import dev.aiengineer.agent.tool.MediaTools;
import dev.aiengineer.agent.tool.Tool;
import dev.aiengineer.agent.tool.WebSearchTools;
import dev.aiengineer.agent.tool.Workspace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

/**
 * Puts GAIA tasks to an agent with web search, section 4.8, and scores the answers like the
 * evaluator of chapter 2 does, so both experiments compare directly. Tasks run concurrently;
 * the semaphores of the LlmClient limit the calls per provider.
 */
@Component
public class GaiaAgentEvaluator {

	static final int MAX_STEPS = 15;

	private final LlmClient llm;

	private final WebSearchTools webSearch;

	private final GaiaAttachments attachments;

	private final String systemPrompt;

	private final String mediaModel;

	public GaiaAgentEvaluator(LlmClient llm, WebSearchTools webSearch, GaiaAttachments attachments,
			@Value("classpath:prompts/gaia-system.txt") Resource systemPrompt,
			@Value("${agent.media.model}") String mediaModel) {
		this.llm = llm;
		this.webSearch = webSearch;
		this.attachments = attachments;
		this.systemPrompt = read(systemPrompt);
		this.mediaModel = mediaModel;
	}

	public List<GaiaResult> evaluate(List<GaiaProblem> tasks, List<String> models) {
		return models.stream().flatMap(model -> evaluate(tasks, model).stream()).toList();
	}

	private List<GaiaResult> evaluate(List<GaiaProblem> tasks, String model) {
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<GaiaResult>> futures = tasks.stream()
				.map(task -> executor.submit(() -> solve(task, model)))
				.toList();
			return futures.stream().map(GaiaAgentEvaluator::get).toList();
		}
	}

	/**
	 * A task with attachment gets its own workspace with the file tools of section 5.4, as in
	 * 5.4.4; the workspace is removed after the run.
	 */
	private GaiaResult solve(GaiaProblem task, String model) {
		Path workspace = null;
		try {
			List<Tool> tools = new ArrayList<>(FunctionTool.allOf(webSearch));
			if (task.hasAttachment()) {
				workspace = attachments.prepareWorkspace(task);
				Workspace files = new Workspace(workspace);
				tools.addAll(FunctionTool.allOf(new FileTools(files)));
				tools.addAll(FunctionTool.allOf(new MediaTools(files, llm, mediaModel)));
			}
			Agent<GaiaOutput> agent = Agent.builder(llm)
				.model(model)
				.name("gaia-agent")
				.instructions(systemPrompt)
				.tools(tools)
				.maxSteps(MAX_STEPS)
				.outputType(GaiaOutput.class)
				.build();
			AgentResult<GaiaOutput> result = agent.run(prompt(task));
			return switch (result.status()) {
				case COMPLETE -> GaiaEvaluator.answered(task, model, result.output());
				case MAX_STEPS -> new GaiaResult(task.taskId(), model, false, null, null, task.finalAnswer(), null,
						"No final answer within " + MAX_STEPS + " steps");
				case ERROR -> GaiaEvaluator.failed(task, model, result.error());
			};
		}
		catch (IOException | RuntimeException ex) {
			return GaiaEvaluator.failed(task, model, ex);
		}
		finally {
			if (workspace != null) {
				delete(workspace);
			}
		}
	}

	/**
	 * The question, and for a task with attachment where to find the file, as Listing 5.22
	 * does; the path is relative to the workspace.
	 */
	public static String prompt(GaiaProblem task) {
		return task.hasAttachment()
				? task.question() + "\n\nThe attached file is located at: " + task.fileName()
				: task.question();
	}

	private static void delete(Path workspace) {
		try {
			FileSystemUtils.deleteRecursively(workspace);
		}
		catch (IOException ex) {
			// a temporary folder left behind does not change the result
		}
	}

	private static GaiaResult get(Future<GaiaResult> future) {
		try {
			return future.get();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while evaluating GAIA tasks", ex);
		}
		catch (ExecutionException ex) {
			throw new IllegalStateException("Evaluating a GAIA task failed", ex.getCause());
		}
	}

	private static String read(Resource resource) {
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read system prompt " + resource, ex);
		}
	}
}
