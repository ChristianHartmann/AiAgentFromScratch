package dev.aiengineer.agent.gaia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.RestClient;

/**
 * Downloads the attachment of the zip task of section 5.4.4. Nothing of its content is
 * printed: the terms of the dataset forbid sharing it.
 */
@Tag("external")
@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class GaiaAttachmentsLiveTest {

	@Autowired
	private RestClient.Builder restClient;

	@Autowired
	private GaiaProperties properties;

	@Autowired
	private GaiaAttachments attachments;

	@Test
	void downloadsTheZipOfTheBookTask() throws IOException {
		assumeTrue(properties.hasToken(), "HF_TOKEN is not set");
		GaiaProblem task = zipTaskOfTheBook(restClient, properties);

		Path workspace = attachments.prepareWorkspace(task);
		try {
			assertThat(workspace.resolve(task.fileName())).isRegularFile();
			assertThat(Files.size(workspace.resolve(task.fileName()))).isPositive();
		}
		finally {
			FileSystemUtils.deleteRecursively(workspace);
		}
	}

	/**
	 * Task bfcd99e1 of section 5.4.4 is level 2; the project's configured split is level 1,
	 * which has no zip task.
	 */
	static GaiaProblem zipTaskOfTheBook(RestClient.Builder restClient, GaiaProperties properties) {
		GaiaDataset level2 = new GaiaDataset(restClient,
				new GaiaProperties(properties.hfToken(), "2023_level2", 1, List.of()));
		return level2.load(100).stream()
			.filter(task -> task.taskId().startsWith("bfcd99e1"))
			.findFirst()
			.orElseThrow();
	}
}
