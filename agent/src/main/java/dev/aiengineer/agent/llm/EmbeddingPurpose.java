package dev.aiengineer.agent.llm;

/**
 * What a text is embedded for. Gemini embeds a question and the passages that may answer it
 * differently; the book embeds both the same way.
 */
public enum EmbeddingPurpose {
	QUERY, DOCUMENT
}
