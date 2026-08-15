package dev.aiengineer.agent.llm;

/**
 * Result of a single call within a batch. A failure in one call must not bring down the
 * whole batch, so it is returned instead of thrown.
 */
public record LlmResult<T>(T value, Exception error) {

	public static <T> LlmResult<T> success(T value) {
		return new LlmResult<>(value, null);
	}

	public static <T> LlmResult<T> failure(Exception error) {
		return new LlmResult<>(null, error);
	}

	public boolean isSuccess() {
		return error == null;
	}
}
