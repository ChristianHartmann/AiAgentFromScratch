package dev.aiengineer.agent.gaia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Loads real GAIA tasks. Nothing of their content is printed or stored: the terms of the
 * dataset forbid sharing it outside a gated repository.
 */
@Tag("llm")
@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class GaiaDatasetLiveTest {

	@Autowired
	private GaiaDataset dataset;

	@Autowired
	private GaiaProperties properties;

	@Test
	void loadsCompleteTasksOfTheConfiguredSplit() {
		assumeTrue(properties.hasToken(), "HF_TOKEN is not set");

		List<GaiaProblem> tasks = dataset.load(5);

		assertThat(tasks).hasSize(5).allSatisfy(task -> {
			assertThat(task.taskId()).isNotBlank();
			assertThat(task.question()).isNotBlank();
			assertThat(task.finalAnswer()).isNotBlank();
			assertThat(task.level()).isEqualTo(1);
		});
	}
}
