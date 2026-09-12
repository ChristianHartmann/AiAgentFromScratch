package dev.aiengineer.agent.gaia;

import dev.aiengineer.agent.agent.Agent;
import dev.aiengineer.agent.agent.AgentResult;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.tool.FunctionTool;
import dev.aiengineer.agent.tool.WebSearchTools;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

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

	private final String systemPrompt;

	public GaiaAgentEvaluator(LlmClient llm, WebSearchTools webSearch,
			@Value("classpath:prompts/gaia-system.txt") Resource systemPrompt) {
		this.llm = llm;
		this.webSearch = webSearch;
		this.systemPrompt = read(systemPrompt);
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

	private GaiaResult solve(GaiaProblem task, String model) {
		Agent<GaiaOutput> agent = Agent.builder(llm)
			.model(model)
			.name("gaia-agent")
			.instructions(systemPrompt)
			.tools(FunctionTool.allOf(webSearch))
			.maxSteps(MAX_STEPS)
			.outputType(GaiaOutput.class)
			.build();
		AgentResult<GaiaOutput> result = agent.run(task.question());
		return switch (result.status()) {
			case COMPLETE -> GaiaEvaluator.answered(task, model, result.output());
			case MAX_STEPS -> new GaiaResult(task.taskId(), model, false, null, null, task.finalAnswer(), null,
					"No final answer within " + MAX_STEPS + " steps");
			case ERROR -> GaiaEvaluator.failed(task, model, result.error());
		};
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
