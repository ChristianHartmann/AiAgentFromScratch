package dev.aiengineer.agent.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-length chunking of section 5.3.2: pieces of a number of characters that overlap, so
 * a sentence cut at one border is whole in the next piece.
 */
public final class Chunker {

	public static final int DEFAULT_SIZE = 500;

	public static final int DEFAULT_OVERLAP = 50;

	private Chunker() {
	}

	/**
	 * Chunks by characters as Listing 5.3 does, trims every chunk and drops empty ones. The
	 * book loops forever when the overlap is not smaller than the size, hence the check.
	 */
	public static List<String> fixedLength(String text, int size, int overlap) {
		if (size <= 0 || overlap < 0 || overlap >= size) {
			throw new IllegalArgumentException(
					"Chunking needs 0 <= overlap < size, got size " + size + " and overlap " + overlap);
		}
		List<String> chunks = new ArrayList<>();
		int start = 0;
		while (start < text.length()) {
			int end = Math.min(start + size, text.length());
			String chunk = text.substring(start, end).strip();
			if (!chunk.isEmpty()) {
				chunks.add(chunk);
			}
			start = end < text.length() ? end - overlap : end;
		}
		return chunks;
	}
}
