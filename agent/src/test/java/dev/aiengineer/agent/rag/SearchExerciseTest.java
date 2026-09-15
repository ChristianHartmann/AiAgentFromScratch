package dev.aiengineer.agent.rag;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.aiengineer.agent.llm.LlmClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The exercise of section 5.3.5 without network: chunk every page with its title, keep the
 * three chunks closest to the question and compare the tokens.
 */
class SearchExerciseTest {

	private final LlmClient llm = mock(LlmClient.class);

	@Test
	void keepsOnlyTheChunksAboutTheQuestionAndSavesMostTokens() {
		KeywordEmbeddings.answerFor(llm);
		List<Chunk> chunks = new ArrayList<>();
		for (SamplePages.Page page : SamplePages.ALL) {
			Chunker.fixedLength("Title: " + page.title() + "\n" + page.text(), Chunker.DEFAULT_SIZE,
					Chunker.DEFAULT_OVERLAP)
				.forEach(text -> chunks.add(new Chunk(text, Map.of("title", page.title()))));
		}

		List<ScoredChunk> top = new VectorSearch(llm).search("quantum physics", chunks, 3);

		int before = TokenCounter.count(SamplePages.ALL.stream().map(SamplePages.Page::text).collect(joining("\n")));
		int after = TokenCounter.count(top.stream().map(scored -> scored.chunk().text()).collect(joining("\n")));
		assertThat(top).hasSize(3)
			.allSatisfy(scored -> assertThat(scored.chunk().metadata()).containsEntry("title", SamplePages.PHYSICS.title()));
		assertThat(after).isLessThan(before / 3);
	}
}
