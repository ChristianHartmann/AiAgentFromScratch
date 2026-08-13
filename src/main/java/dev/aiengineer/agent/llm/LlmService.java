package dev.aiengineer.agent.llm;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * The single entry point for LLM calls. Plays the role LiteLLM plays in the book:
 * callers name a model, everything provider specific happens behind this class.
 */
@Service
public class LlmService {

	private final ModelRouter router;

	public LlmService(ModelRouter router) {
		this.router = router;
	}

	public String complete(String model, List<ChatMessage> messages) {
		ChatResponse response = router.chatModel(model).call(new Prompt(toSpringAi(messages), router.options(model)));
		return response.getResult().getOutput().getText();
	}

	private static List<Message> toSpringAi(List<ChatMessage> messages) {
		return messages.stream().map(LlmService::toSpringAi).toList();
	}

	private static Message toSpringAi(ChatMessage message) {
		return switch (message.role()) {
			case SYSTEM -> new SystemMessage(message.content());
			case USER -> new UserMessage(message.content());
			case ASSISTANT -> new AssistantMessage(message.content());
		};
	}
}
