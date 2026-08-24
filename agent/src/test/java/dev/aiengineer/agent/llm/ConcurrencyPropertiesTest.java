package dev.aiengineer.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ConcurrencyPropertiesTest {

	@Test
	void bindsLowercaseProviderNames() {
		ConcurrencyProperties properties = bind(Map.of("agent.llm.concurrency.limits.google", "5"));

		assertThat(properties.limit(Provider.GOOGLE)).isEqualTo(5);
	}

	@Test
	void keepsDefaultsForProvidersThatAreNotConfigured() {
		ConcurrencyProperties properties = bind(Map.of("agent.llm.concurrency.limits.google", "5"));

		assertThat(properties.limit(Provider.OPENAI)).isEqualTo(8);
		assertThat(properties.limit(Provider.ANTHROPIC)).isEqualTo(4);
	}

	@Test
	void providesDefaultsForEveryProvider() {
		ConcurrencyProperties defaults = ConcurrencyProperties.defaults();

		assertThat(defaults.limit(Provider.OPENAI)).isEqualTo(8);
		assertThat(defaults.limit(Provider.ANTHROPIC)).isEqualTo(4);
		assertThat(defaults.limit(Provider.GOOGLE)).isEqualTo(2);
	}

	private static ConcurrencyProperties bind(Map<String, String> properties) {
		return new Binder(new MapConfigurationPropertySource(properties))
			.bind("agent.llm.concurrency", ConcurrencyProperties.class)
			.orElseGet(ConcurrencyProperties::defaults);
	}
}
