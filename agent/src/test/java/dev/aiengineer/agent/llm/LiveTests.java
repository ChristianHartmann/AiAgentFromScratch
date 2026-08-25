package dev.aiengineer.agent.llm;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Helpers for tests that call real providers.
 */
public final class LiveTests {

	private LiveTests() {
	}

	public static void assumeKeyPresentFor(ModelRouter router, String model) {
		String variable = switch (router.provider(model)) {
			case OPENAI -> "OPENAI_API_KEY";
			case ANTHROPIC -> "ANTHROPIC_API_KEY";
			case GOOGLE -> "GEMINI_API_KEY";
		};
		assumeEnvironmentVariable(variable);
	}

	public static String assumeEnvironmentVariable(String variable) {
		String value = System.getenv(variable);
		assumeTrue(value != null && !value.isBlank(), variable + " is not set");
		return value;
	}
}
