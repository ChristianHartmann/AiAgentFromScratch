package dev.aiengineer.agent.loop;

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
class SimpleAgentLoopLiveTest {

	private static final String MODEL = "google/gemini-3.6-flash";

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

		String answer = new SimpleAgentLoop(llm).run(MODEL,
				"You are a helpful assistant. Always search the web before answering.",
				"Who won the 2025 Nobel Prize in Physics?", FunctionTool.allOf(searchSpy));

		verify(searchSpy, atLeastOnce()).searchWeb(anyString(), any(), any(), any());
		assertThat(answer).containsAnyOf("Clarke", "Devoret", "Martinis");
	}
}
