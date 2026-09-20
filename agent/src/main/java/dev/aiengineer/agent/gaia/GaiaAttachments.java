package dev.aiengineer.agent.gaia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Attachments of GAIA tasks, section 5.4.1. The book downloads every attachment of the split
 * up front and copies them into a workspace before each task (reset_workspace); here only the
 * attachment a task needs is downloaded, kept in a cache, and copied into a fresh workspace
 * per run. The dataset is gated, so the download needs HF_TOKEN; the cache must never be
 * committed.
 */
@Component
public class GaiaAttachments {

	static final String FILES = "https://huggingface.co/datasets/gaia-benchmark/GAIA/resolve/main/";

	private final RestClient restClient;

	private final GaiaProperties properties;

	private final Path cacheDir;

	public GaiaAttachments(RestClient.Builder restClient, GaiaProperties properties,
			@Value("${agent.gaia.cache-dir:.gaia-cache}") Path cacheDir) {
		this.restClient = restClient.build();
		this.properties = properties;
		this.cacheDir = cacheDir.toAbsolutePath().normalize();
	}

	/**
	 * A new temporary folder that holds the task's attachment under its file name. The caller
	 * deletes it after the run.
	 */
	public Path prepareWorkspace(GaiaProblem task) throws IOException {
		if (!task.hasAttachment() || task.filePath().isBlank()) {
			throw new IllegalArgumentException("Task " + task.taskId() + " has no attachment");
		}
		Path cached = cached(task);
		Path workspace = Files.createTempDirectory("gaia-workspace-");
		Files.copy(cached, workspace.resolve(task.fileName()));
		return workspace;
	}

	private Path cached(GaiaProblem task) throws IOException {
		Path file = cacheDir.resolve(task.filePath()).normalize();
		if (!file.startsWith(cacheDir)) {
			throw new IllegalArgumentException("Attachment path " + task.filePath() + " leaves the cache");
		}
		if (Files.exists(file)) {
			return file;
		}
		if (!properties.hasToken()) {
			throw new IllegalStateException("HF_TOKEN is not set, the GAIA dataset is gated");
		}
		byte[] content = restClient.get()
			.uri(FILES + task.filePath())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.hfToken())
			.retrieve()
			.onStatus(HttpStatusCode::isError, (request, response) -> {
				throw new IllegalStateException("Attachment " + task.fileName() + " not reachable (HTTP "
						+ response.getStatusCode().value() + ")");
			})
			.body(byte[].class);
		Files.createDirectories(file.getParent());
		Path partial = Files.createTempFile(file.getParent(), "download-", ".part");
		Files.write(partial, content == null ? new byte[0] : content);
		Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		return file;
	}
}
