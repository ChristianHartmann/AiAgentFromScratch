package dev.aiengineer.agent.llm;

import com.google.genai.Client;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The embedding model of section 5.3.1: Gemini instead of OpenAI's text-embedding-3-small,
 * on the Google client with timeout. Both model auto-configurations are switched off in
 * application.properties; OpenAI's would add a second EmbeddingModel. The connection is a bean
 * of its own because the auto-configured one wants a Vertex AI project without an API key of
 * its own.
 *
 * <p>gemini-embedding-001 and not gemini-embedding-2: the latter ignores the task type and
 * ranks the book's cat/dog pair above cat/kitten.
 */
@Configuration(proxyBeanMethods = false)
public class EmbeddingConfiguration {

	@Bean
	GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(Client googleGenAiClient) {
		return GoogleGenAiEmbeddingConnectionDetails.builder().genAiClient(googleGenAiClient).build();
	}

	@Bean
	EmbeddingModel embeddingModel(GoogleGenAiEmbeddingConnectionDetails connection,
			@Value("${agent.llm.embedding.model:gemini-embedding-001}") String model) {
		return new GoogleGenAiTextEmbeddingModel(connection,
				GoogleGenAiTextEmbeddingOptions.builder().model(model).build());
	}
}
