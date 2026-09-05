package dev.aiengineer.agent.context;

import dev.aiengineer.agent.llm.ContentItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A wrapper with metadata around the content items of one moment in a run, as in section
 * 4.3.1: who produced them, when, and in which execution.
 */
public record Event(String id, String executionId, Instant timestamp, String author, List<ContentItem> content) {

	public Event {
		content = List.copyOf(content);
	}

	public static Event of(String executionId, String author, List<? extends ContentItem> content) {
		return new Event(UUID.randomUUID().toString(), executionId, Instant.now(), author, List.copyOf(content));
	}
}
