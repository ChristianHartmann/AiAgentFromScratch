package dev.aiengineer.agent.gaia;

/**
 * Outcome of one task for one model. {@code isSolvable} is {@code null} and
 * {@code failure} is set when the call itself failed, so there is no self assessment.
 */
public record GaiaResult(String taskId, String model, boolean correct, Boolean isSolvable, String prediction,
		String answer, String unsolvableReason, String failure) {
}
