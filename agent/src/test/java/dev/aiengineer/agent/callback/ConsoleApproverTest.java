package dev.aiengineer.agent.callback;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConsoleApproverTest {

	private final ByteArrayOutputStream printed = new ByteArrayOutputStream();

	@Test
	void approvesOnYes() {
		assertThat(approver("y\n").approve("deleteFile", "{}")).isTrue();
		assertThat(approver("YES\n").approve("deleteFile", "{}")).isTrue();
	}

	@Test
	void deniesOnEverythingElse() {
		assertThat(approver("n\n").approve("deleteFile", "{}")).isFalse();
		assertThat(approver("maybe\n").approve("deleteFile", "{}")).isFalse();
		assertThat(approver("").approve("deleteFile", "{}")).isFalse();
	}

	@Test
	void showsToolAndArgumentsBeforeAsking() {
		approver("n\n").approve("deleteFile", "{\"filePath\":\"a.txt\"}");

		assertThat(printed.toString(StandardCharsets.UTF_8))
			.contains("deleteFile").contains("{\"filePath\":\"a.txt\"}").contains("(y/n)");
	}

	private ConsoleApprover approver(String input) {
		return new ConsoleApprover(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
				new PrintStream(printed, true, StandardCharsets.UTF_8));
	}
}
