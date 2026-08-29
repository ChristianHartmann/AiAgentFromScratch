package dev.aiengineer.agent.tool;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.searxng")
public record SearxngProperties(String url) {

	public SearxngProperties {
		url = url == null || url.isBlank() ? "http://localhost:8888" : url;
	}
}
