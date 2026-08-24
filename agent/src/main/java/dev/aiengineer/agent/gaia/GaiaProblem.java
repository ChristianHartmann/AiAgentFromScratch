package dev.aiengineer.agent.gaia;

/**
 * A single GAIA task. The rows API delivers more fields (file path, annotator metadata),
 * only the ones the experiment needs are kept.
 */
public record GaiaProblem(String taskId, String question, int level, String finalAnswer, String fileName) {

	public boolean hasAttachment() {
		return fileName != null && !fileName.isBlank();
	}
}
