package dev.aiengineer.agent.llm;

import java.util.Arrays;

/**
 * A supported LLM provider together with the prefix that selects it in a model name.
 * OpenAI has no prefix and acts as the default, the way LiteLLM handles it in the book.
 */
public enum Provider {

	OPENAI(""),
	ANTHROPIC("anthropic/"),
	GOOGLE("google/");

	private final String prefix;

	Provider(String prefix) {
		this.prefix = prefix;
	}

	public String prefix() {
		return prefix;
	}

	static Provider ofModel(String model) {
		return Arrays.stream(values())
			.filter(provider -> !provider.prefix.isEmpty() && model.startsWith(provider.prefix))
			.findFirst()
			.orElseGet(() -> {
				if (model.contains("/")) {
					throw new IllegalArgumentException("Unknown provider in model name: " + model);
				}
				return OPENAI;
			});
	}
}
