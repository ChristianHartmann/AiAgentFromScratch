package dev.aiengineer.agent.llm;

/**
 * What a conversation is made of, as in section 4.3.1: messages, tool calls the model asked
 * for, and the results of those calls.
 *
 * <p>The factories return the interface on purpose: items are built to go into lists, and
 * List.of(system(..), user(..)) must stay a List of ContentItem.
 */
public sealed interface ContentItem permits Message, ToolCall, ToolResult {

	static ContentItem system(String content) {
		return new Message(Role.SYSTEM, content);
	}

	static ContentItem user(String content) {
		return new Message(Role.USER, content);
	}

	static ContentItem assistant(String content) {
		return new Message(Role.ASSISTANT, content);
	}
}
