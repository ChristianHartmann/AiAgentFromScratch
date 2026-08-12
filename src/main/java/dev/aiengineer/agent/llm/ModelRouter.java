package dev.aiengineer.agent.llm;

import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Picks the provider from the model name, the way LiteLLM does in the book:
 * "anthropic/claude-haiku-4-5" goes to Anthropic, "google/gemini-2.5-flash" to Google,
 * everything without a prefix to OpenAI.
 */
@Component
public class ModelRouter {

	private final Map<Provider, ChatModel> chatModels;

	@Autowired
	public ModelRouter(@Qualifier("openAiChatModel") ChatModel openAi,
			@Qualifier("anthropicChatModel") ChatModel anthropic,
			@Qualifier("googleGenAiChatModel") ChatModel google) {
		this(Map.of(Provider.OPENAI, openAi, Provider.ANTHROPIC, anthropic, Provider.GOOGLE, google));
	}

	ModelRouter(Map<Provider, ChatModel> chatModels) {
		this.chatModels = Map.copyOf(chatModels);
	}

	public Provider provider(String model) {
		return Provider.ofModel(model);
	}

	public String modelId(String model) {
		Provider provider = provider(model);
		return model.substring(provider.prefix().length());
	}

	public ChatModel chatModel(String model) {
		return chatModels.get(provider(model));
	}
}
