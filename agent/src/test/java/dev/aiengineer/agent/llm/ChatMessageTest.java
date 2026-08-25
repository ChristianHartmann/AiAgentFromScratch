package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ChatMessageTest {

	private final ToolCall call = new ToolCall("call_1", "calculator", "{\"operator\":\"MULTIPLY\"}");

	@Test
	void everyVariantReportsItsRole() {
		assertThat(ChatMessage.system("s").role()).isEqualTo(Role.SYSTEM);
		assertThat(ChatMessage.user("u").role()).isEqualTo(Role.USER);
		assertThat(ChatMessage.assistant("a").role()).isEqualTo(Role.ASSISTANT);
		assertThat(ChatMessage.toolResult(call, "42").role()).isEqualTo(Role.TOOL);
	}

	@Test
	void anAssistantMessageWithoutTextKeepsItsToolCalls() {
		assertThat(ChatMessage.assistant(null, List.of(call)))
			.isEqualTo(new ChatMessage.AssistantMessage("", List.of(call)));
	}

	@Test
	void aPlainAssistantMessageHasNoToolCalls() {
		assertThat(ChatMessage.assistant("Hello")).isEqualTo(new ChatMessage.AssistantMessage("Hello", List.of()));
	}

	@Test
	void aToolResultRefersToItsCall() {
		assertThat(ChatMessage.toolResult(call, "7006652"))
			.isEqualTo(new ChatMessage.ToolResultMessage("call_1", "calculator", "7006652"));
	}

	@Test
	void factoriesComposeIntoAListOfMessages() {
		List<ChatMessage> messages = Stream.of("a", "b")
			.map(text -> List.of(ChatMessage.system("s"), ChatMessage.user(text)))
			.findFirst()
			.orElseThrow();

		assertThat(messages).hasSize(2);
	}
}
