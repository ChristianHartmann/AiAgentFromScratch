package dev.aiengineer.agent.llm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A text message. The provider state is opaque to us: data the provider attached to its
 * answer and expects back unchanged in the next call, such as the thought signatures of
 * Gemini. It only ever sits on assistant messages. Attachments only ever sit on user
 * messages (section 5.4.2).
 */
public record Message(Role role, String content, Map<String, Object> providerState, List<Attachment> attachments)
		implements ContentItem {

	public Message {
		Objects.requireNonNull(role, "role");
		content = content == null ? "" : content;
		providerState = providerState == null ? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(providerState));
		attachments = attachments == null ? List.of() : List.copyOf(attachments);
	}

	public Message(Role role, String content, Map<String, Object> providerState) {
		this(role, content, providerState, List.of());
	}

	public Message(Role role, String content) {
		this(role, content, Map.of());
	}
}
