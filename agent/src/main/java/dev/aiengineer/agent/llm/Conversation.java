package dev.aiengineer.agent.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track of the items of a conversation. Chat APIs are stateless, so the full history
 * has to be sent along with every call.
 */
public class Conversation {

	private final List<ContentItem> messages = new ArrayList<>();

	public static Conversation withSystemPrompt(String systemPrompt) {
		Conversation conversation = new Conversation();
		conversation.add(ContentItem.system(systemPrompt));
		return conversation;
	}

	public void addUser(String content) {
		add(ContentItem.user(content));
	}

	public void addAssistant(String content) {
		add(ContentItem.assistant(content));
	}

	public void add(ContentItem item) {
		messages.add(item);
	}

	public List<ContentItem> messages() {
		return List.copyOf(messages);
	}
}
