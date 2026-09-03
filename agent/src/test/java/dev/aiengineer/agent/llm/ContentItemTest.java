package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ContentItemTest {

	private final ToolCall call = new ToolCall("call_1", "calculator", "{\"operator\":\"MULTIPLY\"}");

	@Test
	void factoriesCreateMessagesWithTheirRole() {
		assertThat(ContentItem.system("s")).isEqualTo(new Message(Role.SYSTEM, "s"));
		assertThat(ContentItem.user("u")).isEqualTo(new Message(Role.USER, "u"));
		assertThat(ContentItem.assistant("a")).isEqualTo(new Message(Role.ASSISTANT, "a"));
	}

	@Test
	void factoriesComposeIntoAListOfContentItems() {
		List<ContentItem> items = Stream.of("a")
			.map(text -> List.of(ContentItem.system("s"), ContentItem.user(text)))
			.findFirst()
			.orElseThrow();

		assertThat(items).hasSize(2);
	}

	@Test
	void aMessageWithoutTextHasEmptyContentAndNoProviderState() {
		Message message = new Message(Role.ASSISTANT, null);

		assertThat(message.content()).isEmpty();
		assertThat(message.providerState()).isEmpty();
	}

	@Test
	void aMessageKeepsTheProviderState() {
		Message message = new Message(Role.ASSISTANT, "", Map.of("thoughtSignatures", List.of("sig")));

		assertThat(message.providerState()).containsEntry("thoughtSignatures", List.of("sig"));
	}

	@Test
	void aToolResultRefersToItsCall() {
		assertThat(ToolResult.success(call, 7006652.0))
			.isEqualTo(new ToolResult("call_1", "calculator", ToolResult.Status.SUCCESS, 7006652.0));
		assertThat(ToolResult.error(call, "Cannot divide by zero"))
			.isEqualTo(new ToolResult("call_1", "calculator", ToolResult.Status.ERROR, "Cannot divide by zero"));
	}
}
