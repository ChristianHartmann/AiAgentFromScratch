package dev.aiengineer.agent.llm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A text message. The provider state is opaque to us: data the provider attached to its
 * answer and expects back unchanged in the next call, such as the thought signatures of
 * Gemini. It only ever sits on assistant messages.
 */
public record Message(Role role, String content, Map<String, Object> providerState) implements ContentItem {

	public Message {
		Objects.requireNonNull(role, "role");
		content = content == null ? "" : content;
		providerState = providerState == null ? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(providerState));
	}

	public Message(Role role, String content) {
		this(role, content, Map.of());
	}
}
