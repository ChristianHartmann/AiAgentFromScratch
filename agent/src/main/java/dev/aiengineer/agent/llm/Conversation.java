package dev.aiengineer.agent.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps track of the messages of a conversation. Chat APIs are stateless, so the
 * full history has to be sent along with every call.
 */
public class Conversation {

	private final List<ChatMessage> messages = new ArrayList<>();

	public static Conversation withSystemPrompt(String systemPrompt) {
		Conversation conversation = new Conversation();
		conversation.messages.add(ChatMessage.system(systemPrompt));
		return conversation;
	}

	public void addUser(String content) {
		messages.add(ChatMessage.user(content));
	}

	public void addAssistant(String content) {
		messages.add(ChatMessage.assistant(content));
	}

	public void add(ChatMessage message) {
		messages.add(message);
	}

	public List<ChatMessage> messages() {
		return Collections.unmodifiableList(messages);
	}
}
