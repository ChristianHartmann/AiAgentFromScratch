package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConversationTest {

	@Test
	void keepsSystemPromptInFirstPosition() {
		Conversation conversation = Conversation.withSystemPrompt("You are helpful.");

		conversation.addUser("My name is Max.");

		assertThat(conversation.messages())
			.containsExactly(
				ContentItem.system("You are helpful."),
				ContentItem.user("My name is Max."));
	}

	@Test
	void appendsMessagesInOrderOfTheConversation() {
		Conversation conversation = new Conversation();

		conversation.addUser("My name is Max.");
		conversation.addAssistant("Hello Max.");
		conversation.addUser("What is my name?");

		assertThat(conversation.messages()).extracting(item -> ((Message) item).role())
			.containsExactly(Role.USER, Role.ASSISTANT, Role.USER);
	}

	@Test
	void returnsHistoryAsUnmodifiableList() {
		Conversation conversation = new Conversation();
		conversation.addUser("Hello");

		var messages = conversation.messages();

		assertThat(messages).isUnmodifiable();
	}

	@Test
	void returnsASnapshotThatLaterMessagesDoNotChange() {
		Conversation conversation = new Conversation();
		conversation.addUser("First");

		var snapshot = conversation.messages();
		conversation.addUser("Second");

		assertThat(snapshot).hasSize(1);
	}
}
