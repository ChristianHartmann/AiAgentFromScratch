package dev.aiengineer.agent.llm;

import com.anthropic.models.messages.ToolChoiceAny;
import com.anthropic.models.messages.ToolChoiceAuto;
import java.util.List;
import java.util.Map;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
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
		return builder(model).build();
	}

	/**
	 * Like {@link #options(String)}, but asks the provider for an answer that matches the
	 * given JSON schema, using its native structured output feature.
	 */
	public ChatOptions options(String model, String outputSchema) {
		return builder(model).outputSchema(outputSchema).build();
	}

	/**
	 * Like {@link #options(String)}, but offers the given tools to the model and, unless the
	 * choice is null, tells it whether it may answer with text or has to call a tool. The
	 * builders of all three providers implement the tool calling builder, hence the cast.
	 */
	public ChatOptions options(String model, List<ToolDefinition> tools, ToolChoice toolChoice) {
		StructuredOutputChatOptions.Builder<?> builder = builder(model);
		List<ToolCallback> callbacks = tools.stream().<ToolCallback>map(DefinitionOnlyToolCallback::new).toList();
		((ToolCallingChatOptions.Builder<?>) builder).toolCallbacks(callbacks);
		if (toolChoice != null) {
			applyToolChoice(model, builder, toolChoice);
		}
		return builder.build();
	}

	private void applyToolChoice(String model, StructuredOutputChatOptions.Builder<?> builder, ToolChoice toolChoice) {
		boolean required = toolChoice == ToolChoice.REQUIRED;
		switch (provider(model)) {
			case OPENAI -> ((OpenAiChatOptions.Builder) builder).toolChoice(required ? "required" : "auto");
			case ANTHROPIC -> ((AnthropicChatOptions.Builder) builder).toolChoice(required
					? com.anthropic.models.messages.ToolChoice.ofAny(ToolChoiceAny.builder().build())
					: com.anthropic.models.messages.ToolChoice.ofAuto(ToolChoiceAuto.builder().build()));
			case GOOGLE -> ((GoogleGenAiChatOptions.Builder) builder).toolChoice(
					new GoogleGenAiChatOptions.ToolChoice(required ? GoogleGenAiChatOptions.ToolChoice.Mode.ANY
							: GoogleGenAiChatOptions.ToolChoice.Mode.AUTO, List.of()));
		}
	}

	private StructuredOutputChatOptions.Builder<?> builder(String model) {
		StructuredOutputChatOptions.Builder<?> builder = switch (provider(model)) {
			case OPENAI -> OpenAiChatOptions.builder();
			case ANTHROPIC -> AnthropicChatOptions.builder();
			case GOOGLE -> GoogleGenAiChatOptions.builder();
		};
		return builder.model(modelId(model));
	}
}
