package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTest {

	@Test
	void splitsRowsAndFields() {
		assertThat(Csv.parse("a,b\n1,2\n")).containsExactly(List.of("a", "b"), List.of("1", "2"));
	}

	@Test
	void keepsCommasLineBreaksAndQuotesInsideQuotedFields() {
		assertThat(Csv.parse("name,note\r\n\"Smith, J.\",\"said \"\"hi\"\"\nthen left\"\r\n"))
			.containsExactly(List.of("name", "note"), List.of("Smith, J.", "said \"hi\"\nthen left"));
	}

	@Test
	void keepsTheLastRowWithoutLineBreak() {
		assertThat(Csv.parse("a,b\n1,")).containsExactly(List.of("a", "b"), List.of("1", ""));
	}
}
