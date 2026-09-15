package dev.aiengineer.agent.rag;

import java.util.Map;
import java.util.Objects;

/**
 * A piece of text with where it came from, such as title and URL of a web page. Listing 5.9
 * builds such chunks and then searches only their texts; here the metadata stays attached.
 */
public record Chunk(String text, Map<String, String> metadata) {

	public Chunk {
		Objects.requireNonNull(text, "text");
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}

	public Chunk(String text) {
		this(text, Map.of());
	}
}
