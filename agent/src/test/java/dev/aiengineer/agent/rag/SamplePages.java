package dev.aiengineer.agent.rag;

import java.util.List;

/**
 * Three made-up web pages for the exercise of section 5.3.5, written for the tests. Each is
 * long enough for several chunks, only one is about quantum physics.
 */
public final class SamplePages {

	public record Page(String title, String url, String text) {
	}

	public static final Page PHYSICS = new Page("Prize for quantum physics", "https://example.com/physics", repeat("""
			The committee awarded this year's prize in physics to three researchers for their work on quantum \
			circuits. Their experiments showed that quantum effects appear in electrical circuits large enough \
			to hold in a hand. Quantum tunnelling, known from single particles, was measured in a circuit on a chip. \
			The physics community sees the result as a basis for quantum computers built from such circuits. \
			One of the laureates said that physics had waited decades for a clean measurement of this kind. \
			"""));

	public static final Page LITERATURE = new Page("Prize for literature", "https://example.com/literature", repeat("""
			The prize in literature went to a writer whose novels follow families across three generations. \
			Critics praised the novel cycle for its quiet language and its patience with small moments. \
			Her first novel appeared thirty years ago and was translated into more than forty languages. \
			The academy said her literature gives a voice to people who rarely appear in novels. \
			Bookshops reported that her latest novel sold out within a day of the announcement. \
			"""));

	public static final Page PEACE = new Page("Prize for peace", "https://example.com/peace", repeat("""
			The peace prize honours a group of mediators who brought two neighbouring states to sign a treaty. \
			The treaty ends a border conflict that had lasted for more than twenty years. \
			Negotiations for peace took place in secret over four years in three different countries. \
			The committee called the treaty an example of patient work for peace far from the cameras. \
			Both governments promised to open the border crossings within six months of the treaty. \
			"""));

	public static final List<Page> ALL = List.of(PHYSICS, LITERATURE, PEACE);

	private SamplePages() {
	}

	private static String repeat(String paragraph) {
		return (paragraph + "\n").repeat(5).strip();
	}
}
