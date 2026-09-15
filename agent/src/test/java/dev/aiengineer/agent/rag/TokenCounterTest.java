package dev.aiengineer.agent.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenCounterTest {

	@Test
	void countsWithTheEncodingOfCurrentOpenAiModels() {
		assertThat(TokenCounter.count("Hello, world!")).isEqualTo(4);
		assertThat(TokenCounter.count("The quick brown fox jumps over the lazy dog.")).isEqualTo(10);
	}

	@Test
	void countsNothingForAnEmptyText() {
		assertThat(TokenCounter.count("")).isZero();
		assertThat(TokenCounter.count(null)).isZero();
	}
}
