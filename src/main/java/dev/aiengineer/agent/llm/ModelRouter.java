package dev.aiengineer.agent.llm;

import java.util.Map;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Picks the provider from the model name, the way LiteLLM does in the book:
 * "anthropic/claude-haiku-4-5" goes to Anthropic, "google/gemini-3.6-flash" to Google,
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

	/**
	 * Builds the options in the type the provider's model implementation expects. The
	 * generic {@link ChatOptions} cannot be used here: every provider casts the options
	 * of a prompt to its own type.
	 */
	public ChatOptions options(String model) {
		String modelId = modelId(model);
		return switch (provider(model)) {
			case OPENAI -> OpenAiChatOptions.builder().model(modelId).build();
			case ANTHROPIC -> AnthropicChatOptions.builder().model(modelId).build();
			case GOOGLE -> GoogleGenAiChatOptions.builder().model(modelId).build();
		};
	}
}
