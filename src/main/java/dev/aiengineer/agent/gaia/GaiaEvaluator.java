package dev.aiengineer.agent.gaia;

import dev.aiengineer.agent.llm.ChatMessage;
import dev.aiengineer.agent.llm.LlmRefusalException;
import dev.aiengineer.agent.llm.LlmResult;
import dev.aiengineer.agent.llm.LlmService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Puts GAIA tasks to models without any tools and compares their answers with the ground
 * truth. The system prompt is the GAIA evaluation prompt from the book's repository
 * (scratch_agents/eval/gaia.py, MIT license), with the field names of {@link GaiaOutput}.
 */
@Component
public class GaiaEvaluator {

	private final LlmService llm;

	private final String systemPrompt;

	public GaiaEvaluator(LlmService llm, @Value("classpath:prompts/gaia-system.txt") Resource systemPrompt) {
		this.llm = llm;
		this.systemPrompt = read(systemPrompt);
	}

	/**
	 * Evaluates every task against every model. All tasks of a model go out as one
	 * concurrent batch, the models follow one another.
	 */
	public List<GaiaResult> evaluate(List<GaiaProblem> tasks, List<String> models) {
		return models.stream().flatMap(model -> evaluate(tasks, model).stream()).toList();
	}

	/**
	 * GAIA uses exact match, only case and surrounding whitespace are forgiven.
	 */
	static boolean isCorrect(String prediction, String answer) {
		return prediction != null && prediction.strip().equalsIgnoreCase(answer.strip());
	}

	private List<GaiaResult> evaluate(List<GaiaProblem> tasks, String model) {
		List<List<ChatMessage>> batch = tasks.stream()
			.map(task -> List.of(ChatMessage.system(systemPrompt), ChatMessage.user(task.question())))
			.toList();
		List<LlmResult<GaiaOutput>> outputs = llm.completeAll(model, batch, GaiaOutput.class);
		return IntStream.range(0, tasks.size())
			.mapToObj(i -> toResult(tasks.get(i), model, outputs.get(i)))
			.toList();
	}

	private static GaiaResult toResult(GaiaProblem task, String model, LlmResult<GaiaOutput> output) {
		if (output.isSuccess()) {
			GaiaOutput answer = output.value();
			return new GaiaResult(task.taskId(), model, isCorrect(answer.finalAnswer(), task.finalAnswer()),
					answer.isSolvable(), answer.finalAnswer(), task.finalAnswer(), answer.unsolvableReason(), null);
		}
		if (output.error() instanceof LlmRefusalException refusal) {
			return new GaiaResult(task.taskId(), model, false, false, "", task.finalAnswer(),
					"Model refused to answer (finish reason: " + refusal.finishReason() + ")", null);
		}
		return new GaiaResult(task.taskId(), model, false, null, null, task.finalAnswer(), null,
				output.error().getMessage());
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
