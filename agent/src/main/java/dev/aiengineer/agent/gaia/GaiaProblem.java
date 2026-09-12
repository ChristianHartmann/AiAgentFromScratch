package dev.aiengineer.agent.gaia;

import java.util.Locale;

/**
 * A single GAIA task. The rows API delivers more fields (file path, annotator metadata),
 * only the ones the experiments need are kept: of the annotator metadata only the tools,
 * which select the tasks for the agent of section 4.8.
 */
public record GaiaProblem(String taskId, String question, int level, String finalAnswer, String fileName,
		String annotatorTools) {

	public GaiaProblem(String taskId, String question, int level, String finalAnswer, String fileName) {
		this(taskId, question, level, finalAnswer, fileName, "");
	}

	public boolean hasAttachment() {
		return fileName != null && !fileName.isBlank();
	}

	/**
	 * Whether the annotators needed a web search, the selection of section 4.8.
	 */
	public boolean needsWebSearch() {
		String tools = annotatorTools == null ? "" : annotatorTools.toLowerCase(Locale.ROOT);
		return tools.contains("search") || tools.contains("browser");
	}
}
