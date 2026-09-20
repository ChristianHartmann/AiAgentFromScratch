package dev.aiengineer.agent.gaia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.RestClient;

class GaiaAttachmentsTest {

	private static final String URL = GaiaAttachments.FILES + "2023/validation/chronicle.pdf";

	@TempDir
	Path cache;

	private final RestClient.Builder restClient = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClient).build();

	private final List<Path> workspaces = new ArrayList<>();

	private final GaiaProblem task = new GaiaProblem("t1", "Question?", 1, "42", "chronicle.pdf", "",
			"2023/validation/chronicle.pdf");

	@AfterEach
	void removeWorkspaces() throws IOException {
		for (Path workspace : workspaces) {
			FileSystemUtils.deleteRecursively(workspace);
		}
	}

	@Test
	void downloadsTheAttachmentWithTheTokenIntoAFreshWorkspace() throws IOException {
		server.expect(once(), requestTo(URL))
			.andExpect(header("Authorization", "Bearer test-token"))
			.andRespond(withSuccess(new byte[] { 1, 2, 3 }, MediaType.APPLICATION_OCTET_STREAM));

		Path workspace = attachments("test-token").prepareWorkspace(task);

		assertThat(workspace.resolve("chronicle.pdf")).hasBinaryContent(new byte[] { 1, 2, 3 });
		assertThat(workspace).isNotEqualTo(cache);
		server.verify();
	}

	@Test
	void downloadsEveryAttachmentOnlyOnce() throws IOException {
		server.expect(once(), requestTo(URL))
			.andRespond(withSuccess(new byte[] { 1, 2, 3 }, MediaType.APPLICATION_OCTET_STREAM));
		GaiaAttachments attachments = attachments("test-token");

		Path first = attachments.prepareWorkspace(task);
		Path second = attachments.prepareWorkspace(task);

		assertThat(first).isNotEqualTo(second);
		assertThat(second.resolve("chronicle.pdf")).exists();
		server.verify();
	}

	@Test
	void startsEveryWorkspaceWithoutTheChangesOfTheLastRun() throws IOException {
		server.expect(once(), requestTo(URL))
			.andRespond(withSuccess(new byte[] { 1 }, MediaType.APPLICATION_OCTET_STREAM));
		GaiaAttachments attachments = attachments("test-token");
		Path first = attachments.prepareWorkspace(task);
		Files.writeString(first.resolve("left-over.txt"), "x");

		assertThat(attachments.prepareWorkspace(task).resolve("left-over.txt")).doesNotExist();
	}

	@Test
	void reportsAFailedDownload() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> attachments("wrong-token").prepareWorkspace(task))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("401");
	}

	@Test
	void refusesTasksWithoutAttachment() {
		GaiaProblem plain = new GaiaProblem("t2", "Question?", 1, "42", "");

		assertThatThrownBy(() -> attachments("test-token").prepareWorkspace(plain))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void refusesAPathThatLeavesTheCache() {
		GaiaProblem evil = new GaiaProblem("t3", "Question?", 1, "42", "x.pdf", "", "../../x.pdf");

		assertThatThrownBy(() -> attachments("test-token").prepareWorkspace(evil))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void refusesToDownloadWithoutAToken() {
		assertThatThrownBy(() -> attachments("").prepareWorkspace(task))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("HF_TOKEN");
	}

	private GaiaAttachments attachments(String token) {
		return new GaiaAttachments(restClient, new GaiaProperties(token, "2023_level1", 5, List.of()), cache) {

			@Override
			public Path prepareWorkspace(GaiaProblem task) throws IOException {
				Path workspace = super.prepareWorkspace(task);
				workspaces.add(workspace);
				return workspace;
			}
		};
	}
}
