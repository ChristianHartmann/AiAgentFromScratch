package dev.aiengineer.agent.gaia;

/**
 * The answer we ask the model for. Its JSON schema goes to the provider as structured
 * output, so the field names have to match the ones the system prompt talks about.
 */
public record GaiaOutput(boolean isSolvable, String unsolvableReason, String finalAnswer) {
}
