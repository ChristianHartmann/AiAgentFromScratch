package dev.aiengineer.agent.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

	@Test
	void cutsWithOverlapAsInListing54() {
		List<String> chunks = Chunker.fixedLength("A".repeat(283), 100, 20);

		assertThat(chunks).extracting(String::length).containsExactly(100, 100, 100, 43);
	}

	@Test
	void cutsIntoThreeChunksWithoutOverlap() {
		assertThat(Chunker.fixedLength("A".repeat(283), 100, 0)).hasSize(3);
	}

	@Test
	void repeatsTheOverlapAtTheStartOfTheNextChunk() {
		List<String> chunks = Chunker.fixedLength("abcdefghij", 6, 2);

		assertThat(chunks).containsExactly("abcdef", "efghij");
	}

	@Test
	void trimsChunksAndDropsEmptyOnes() {
		assertThat(Chunker.fixedLength("abc" + " ".repeat(10), 5, 0)).containsExactly("abc");
	}

	@Test
	void returnsNothingForAnEmptyText() {
		assertThat(Chunker.fixedLength("", 500, 50)).isEmpty();
	}

	@Test
	void rejectsAnOverlapThatWouldLoopForever() {
		assertThatThrownBy(() -> Chunker.fixedLength("abc", 10, 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Chunker.fixedLength("abc", 10, -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Chunker.fixedLength("abc", 0, 0)).isInstanceOf(IllegalArgumentException.class);
	}
}
