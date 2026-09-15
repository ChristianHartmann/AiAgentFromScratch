package dev.aiengineer.agent.rag;

/**
 * A chunk with its cosine similarity to the query, from -1 to 1.
 */
public record ScoredChunk(Chunk chunk, double score) {
}
