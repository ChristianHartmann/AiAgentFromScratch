package dev.aiengineer.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.aiengineer.agent.llm.LiveTests;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.ModelRouter;
import dev.aiengineer.agent.tool.FunctionTool;
import dev.aiengineer.agent.tool.WebSearchTools;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("llm")
@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class AgentLiveTest {

	private static final String MODEL = "google/gemini-3.6-flash";

	public enum Mood {
		POSITIVE, NEGATIVE, NEUTRAL
	}

	public record Sentiment(Mood sentiment, double confidence, List<String> keyPhrases) {
	}

	@Autowired
	private LlmClient llm;

	@Autowired
	private ModelRouter router;

	@Autowired
	private WebSearchTools webSearch;

	@Test
	void answersACurrentQuestionThroughTheWebSearch() {
		LiveTests.assumeKeyPresentFor(router, MODEL);
		LiveTests.assumeSearxngRunning();
		WebSearchTools searchSpy = spy(webSearch);

		AgentResult<String> result = Agent.builder(llm)
			.model(MODEL)
			.instructions("You are a helpful assistant. Always search the web before answering.")
			.tools(FunctionTool.allOf(searchSpy))
			.build()
			.run("Who won the 2025 Nobel Prize in Physics?");

		verify(searchSpy, atLeastOnce()).searchWeb(anyString(), any(), any(), any(), any());
		assertThat(result.status()).isEqualTo(AgentResult.Status.COMPLETE);
		assertThat(result.output()).containsAnyOf("Clarke", "Devoret", "Martinis");
	}

	@Test
	void returnsATypedAnswerThroughFinalAnswer() {
		LiveTests.assumeKeyPresentFor(router, MODEL);

		AgentResult<Sentiment> result = Agent.builder(llm)
			.model(MODEL)
			.instructions("Analyze the sentiment of the text.")
			.outputType(Sentiment.class)
			.build()
			.run("I absolutely love this product, it changed my mornings.");

		assertThat(result.status()).isEqualTo(AgentResult.Status.COMPLETE);
		assertThat(result.output().sentiment()).isEqualTo(Mood.POSITIVE);
		assertThat(result.output().keyPhrases()).isNotEmpty();
	}
}
