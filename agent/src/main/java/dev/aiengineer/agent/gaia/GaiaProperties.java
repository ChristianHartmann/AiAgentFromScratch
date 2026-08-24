package dev.aiengineer.agent.gaia;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the GAIA experiment. The token comes from HF_TOKEN, the dataset is gated.
 */
@ConfigurationProperties(prefix = "agent.gaia")
public record GaiaProperties(String hfToken, String split, int problemCount, List<String> models) {

	public GaiaProperties {
		models = models == null ? List.of() : List.copyOf(models);
	}

	public boolean hasToken() {
		return hfToken != null && !hfToken.isBlank();
	}
}
