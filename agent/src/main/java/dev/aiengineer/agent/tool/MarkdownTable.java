package dev.aiengineer.agent.tool;

import java.util.List;

/**
 * Renders rows as a Markdown table, the first row as header, as pandas' to_markdown does in
 * the book.
 */
final class MarkdownTable {

	private MarkdownTable() {
	}

	static String of(List<List<String>> rows) {
		if (rows.isEmpty()) {
			return "(no rows)";
		}
		int columns = rows.stream().mapToInt(List::size).max().orElse(0);
		StringBuilder table = new StringBuilder();
		table.append(row(rows.getFirst(), columns)).append('\n');
		table.append('|').append(" --- |".repeat(columns)).append('\n');
		rows.stream().skip(1).forEach(row -> table.append(row(row, columns)).append('\n'));
		return table.toString().stripTrailing();
	}

	private static String row(List<String> cells, int columns) {
		StringBuilder row = new StringBuilder("|");
		for (int i = 0; i < columns; i++) {
			row.append(' ').append(i < cells.size() ? escape(cells.get(i)) : "").append(" |");
		}
		return row.toString();
	}

	private static String escape(String cell) {
		return cell.replace("|", "\\|").replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').strip();
	}
}
