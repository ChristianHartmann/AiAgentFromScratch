package dev.aiengineer.agent.gaia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.callback.SearchResultCompressor;
import dev.aiengineer.agent.llm.ContentItem;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.LlmRefusalException;
import dev.aiengineer.agent.llm.LlmRequest;
import dev.aiengineer.agent.llm.LlmResponse;
import dev.aiengineer.agent.llm.Message;
import dev.aiengineer.agent.llm.Role;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.Usage;
import dev.aiengineer.agent.tool.PageFetcher;
import dev.aiengineer.agent.tool.SearxngProperties;
import dev.aiengineer.agent.tool.WebSearchTools;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.RestClient;

class GaiaAgentEvaluatorTest {

	private static final String GEMINI = "google/gemini-3.6-flash";

	private final GaiaProblem task = new GaiaProblem("t1", "Which river?", 1, "Sample River", "",
			"1. A web browser.\n2. A search engine.");

	private final LlmClient llm = mock(LlmClient.class);

	private final GaiaAttachments attachments = mock(GaiaAttachments.class);

	private final GaiaAgentEvaluator evaluator = new GaiaAgentEvaluator(llm,
			new WebSearchTools(RestClient.builder(), new SearxngProperties("http://localhost:8888"), new PageFetcher()),
			attachments, new ByteArrayResource("system prompt".getBytes()), GEMINI);

	private final GaiaProblem withFile = new GaiaProblem("t2", "How many applicants qualify?", 2, "17",
			"job.zip", "1. A file reader", "2023/validation/job.zip");

	@TempDir
	Path temp;

	private Path workspace;

	@BeforeEach
	void createWorkspace() throws IOException {
		workspace = Files.createDirectory(temp.resolve("workspace"));
	}

	@Test
	void countsACorrectFinalAnswer() {
		when(llm.generate(any())).thenReturn(finalAnswer("{\"isSolvable\":true,\"unsolvableReason\":\"\",\"finalAnswer\":\"sample river\"}"));

		GaiaResult result = evaluator.evaluate(List.of(task), List.of(GEMINI)).getFirst();

		assertThat(result.correct()).isTrue();
		assertThat(result.isSolvable()).isTrue();
		assertThat(result.failure()).isNull();
	}

	@Test
	void offersTheWebSearchAndTheGaiaPrompt() {
		when(llm.generate(any())).thenReturn(finalAnswer("{\"isSolvable\":true,\"unsolvableReason\":\"\",\"finalAnswer\":\"x\"}"));

		evaluator.evaluate(List.of(task), List.of(GEMINI));

		ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
		verify(llm, atLeastOnce()).generate(request.capture());
		assertThat(request.getValue().instructions()).containsExactly("system prompt");
		assertThat(request.getValue().tools()).extracting(definition -> definition.name())
			.containsExactly("searchWeb", "final_answer");
	}

	@Test
	void countsARunWithoutFinalAnswerAsWrongWithReason() {
		when(llm.generate(any())).thenReturn(new LlmResponse(List.of(new Message(Role.ASSISTANT, "thinking")), Usage.NONE));

		GaiaResult result = evaluator.evaluate(List.of(task), List.of(GEMINI)).getFirst();

		assertThat(result.correct()).isFalse();
		assertThat(result.isSolvable()).isNull();
		assertThat(result.failure()).contains("15");
	}

	@Test
	void countsAFailingRunAsWrongWithTheError() {
		when(llm.generate(any())).thenThrow(new IllegalStateException("429 quota"));

		GaiaResult result = evaluator.evaluate(List.of(task), List.of(GEMINI)).getFirst();

		assertThat(result.correct()).isFalse();
		assertThat(result.failure()).contains("429 quota");
	}

	@Test
	void countsARefusalAsUnsolvableLikeTheEvaluatorOfChapter2() {
		when(llm.generate(any())).thenThrow(new LlmRefusalException(GEMINI, "SAFETY"));

		GaiaResult result = evaluator.evaluate(List.of(task), List.of(GEMINI)).getFirst();

		assertThat(result.correct()).isFalse();
		assertThat(result.isSolvable()).isFalse();
		assertThat(result.failure()).isNull();
		assertThat(result.unsolvableReason()).contains("refused").contains("SAFETY");
	}

	@Test
	void offersTheFileToolsAndNamesTheFileForATaskWithAttachment() throws IOException {
		Files.write(workspace.resolve("job.zip"), new byte[] { 1 });
		when(attachments.prepareWorkspace(withFile)).thenReturn(workspace);
		when(llm.generate(any())).thenReturn(finalAnswer("{\"isSolvable\":true,\"unsolvableReason\":\"\",\"finalAnswer\":\"17\"}"));

		GaiaResult result = evaluator.evaluate(List.of(withFile), List.of(GEMINI)).getFirst();

		assertThat(result.correct()).isTrue();
		ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
		verify(llm, atLeastOnce()).generate(request.capture());
		assertThat(request.getValue().tools()).extracting(definition -> definition.name())
			.contains("unzipFile", "listFiles", "readFile", "readMediaFile");
		assertThat(request.getValue().contents().getFirst())
			.isEqualTo(ContentItem.user("How many applicants qualify?\n\nThe attached file is located at: job.zip"));
	}

	@Test
	void removesTheWorkspaceAfterTheRun() throws IOException {
		when(attachments.prepareWorkspace(withFile)).thenReturn(workspace);
		when(llm.generate(any())).thenReturn(finalAnswer("{\"isSolvable\":true,\"unsolvableReason\":\"\",\"finalAnswer\":\"17\"}"));

		evaluator.evaluate(List.of(withFile), List.of(GEMINI));

		assertThat(workspace).doesNotExist();
	}

	@Test
	void countsAFailedDownloadAsWrongWithTheReason() throws IOException {
		when(attachments.prepareWorkspace(withFile)).thenThrow(new IllegalStateException("HTTP 401"));

		GaiaResult result = evaluator.evaluate(List.of(withFile), List.of(GEMINI)).getFirst();

		assertThat(result.correct()).isFalse();
		assertThat(result.failure()).contains("401");
	}

	@Test
	void shortensSearchResultsWithTheCompressor() {
		assertThat(evaluator.afterToolCallbacks()).singleElement()
			.isInstanceOf(SearchResultCompressor.class);
	}

	private static LlmResponse finalAnswer(String output) {
		List<ContentItem> content = new ArrayList<>();
		content.add(new Message(Role.ASSISTANT, ""));
		content.add(new ToolCall("call_1", "final_answer", "{\"output\":" + output + "}"));
		return new LlmResponse(content, Usage.NONE);
	}
}
