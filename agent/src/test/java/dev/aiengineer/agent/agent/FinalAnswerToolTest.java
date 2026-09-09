package dev.aiengineer.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.aiengineer.agent.context.ExecutionContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FinalAnswerToolTest {

	public enum Mood {
		POSITIVE, NEGATIVE, NEUTRAL
	}

	public record Sentiment(Mood sentiment, double confidence, List<String> keyPhrases) {
	}

	private final FinalAnswerTool<Sentiment> tool = new FinalAnswerTool<>(Sentiment.class);

	@Test
	void isNamedFinalAnswer() {
		assertThat(tool.name()).isEqualTo("final_answer");
		assertThat(tool.description()).isEqualTo("Return the final structured answer matching the required schema.");
	}

	@Test
	void wrapsTheSchemaOfTheOutputTypeInAnOutputProperty() {
		JsonNode schema = JsonMapper.shared().readTree(tool.definition().inputSchema());

		assertThat(schema.path("required").values()).extracting(JsonNode::asString).containsExactly("output");
		JsonNode output = schema.path("properties").path("output");
		assertThat(output.path("properties").has("sentiment")).isTrue();
		assertThat(output.path("properties").has("keyPhrases")).isTrue();
		assertThat(output.has("$schema")).isFalse();
	}

	@Test
	void turnsTheOutputIntoTheRecord() throws Exception {
		Object result = tool.execute(new ExecutionContext(),
				"{\"output\":{\"sentiment\":\"positive\",\"confidence\":0.9,\"keyPhrases\":[\"great\"]}}");

		assertThat(result).isEqualTo(new Sentiment(Mood.POSITIVE, 0.9, List.of("great")));
	}

	@Test
	void rejectsAnswersWithoutOutput() {
		assertThatThrownBy(() -> tool.execute(new ExecutionContext(), "{\"sentiment\":\"positive\"}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("output");
	}

	@Test
	void rejectsOutputThatDoesNotMatchTheType() {
		assertThatThrownBy(() -> tool.execute(new ExecutionContext(), "{\"output\":{\"sentiment\":\"furious\"}}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("POSITIVE");
	}
}
