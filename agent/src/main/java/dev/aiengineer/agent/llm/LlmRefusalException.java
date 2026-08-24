package dev.aiengineer.agent.llm;

/**
 * The model answered without any text. Providers do this when they refuse a request, for
 * example for safety reasons, and report the cause only in the finish reason.
 */
public class LlmRefusalException extends RuntimeException {

	private final String finishReason;

	public LlmRefusalException(String model, String finishReason) {
		super(model + " returned no answer (finish reason: " + finishReason + ")");
		this.finishReason = finishReason;
	}

	public String finishReason() {
		return finishReason;
	}
}
