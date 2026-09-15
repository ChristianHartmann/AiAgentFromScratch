package dev.aiengineer.agent.rag;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * Counts tokens with o200k_base, the encoding tiktoken uses for current OpenAI models in the
 * book. Exact for those models, an estimate for Gemini, which counts with its own tokenizer.
 */
public final class TokenCounter {

	private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
		.getEncoding(EncodingType.O200K_BASE);

	private TokenCounter() {
	}

	public static int count(String text) {
		return text == null || text.isEmpty() ? 0 : ENCODING.countTokens(text);
	}
}
