package dev.aiengineer.agent.llm;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upper bound of concurrent calls per provider, the counterpart of the semaphores in the
 * book. Providers without a configured value keep their default.
 */
@ConfigurationProperties(prefix = "agent.llm.concurrency")
public record ConcurrencyProperties(Map<Provider, Integer> limits) {

	private static final Map<Provider, Integer> DEFAULTS = Map.of(
		Provider.OPENAI, 8,
		Provider.ANTHROPIC, 4,
		Provider.GOOGLE, 2);

	public ConcurrencyProperties {
		Map<Provider, Integer> merged = new EnumMap<>(DEFAULTS);
		if (limits != null) {
			merged.putAll(limits);
		}
		limits = Map.copyOf(merged);
	}

	public static ConcurrencyProperties defaults() {
		return new ConcurrencyProperties(Map.of());
	}

	public int limit(Provider provider) {
		return limits.get(provider);
	}
}
