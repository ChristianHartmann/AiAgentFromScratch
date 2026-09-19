package dev.aiengineer.agent.tool;

import dev.aiengineer.agent.llm.Attachment;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.Message;
import dev.aiengineer.agent.llm.Role;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * read_media_file of section 5.4.2. The book sends images to a fixed OpenAI model and
 * renders PDF pages to images first; Gemini reads images, audio and PDF directly, so the file
 * goes as it is, with the question, to a configurable model.
 */
public class MediaTools {

	static final long MAX_BYTES = 20_000_000;

	private static final Map<String, String> MIME_TYPES = Map.ofEntries(
			Map.entry("pdf", "application/pdf"),
			Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
			Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("bmp", "image/bmp"),
			Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"), Map.entry("m4a", "audio/mp4"),
			Map.entry("flac", "audio/flac"), Map.entry("ogg", "audio/ogg"), Map.entry("webm", "audio/webm"));

	private final Workspace workspace;

	private final LlmClient llm;

	private final String model;

	public MediaTools(Workspace workspace, LlmClient llm, String model) {
		this.workspace = workspace;
		this.llm = llm;
		this.model = model;
	}

	@ToolFunction("Answer a question about a PDF, image or audio file in the workspace. The file goes to a model "
			+ "that reads, sees and hears it.")
	public String readMediaFile(@ToolParam("Path of the file, relative to the workspace") String filePath,
			@ToolParam("What to find out from the file") String query) throws IOException {
		Path file = workspace.resolve(filePath);
		String mimeType = MIME_TYPES.get(FileTools.extensionOf(file));
		if (mimeType == null) {
			throw new IllegalArgumentException(filePath + " is no PDF, image or audio file, use readFile");
		}
		if (Files.size(file) > MAX_BYTES) {
			throw new IllegalArgumentException(filePath + " is larger than " + MAX_BYTES / 1_000_000 + " MB");
		}
		Message question = new Message(Role.USER, query, Map.of(),
				List.of(new Attachment(Files.readAllBytes(file), mimeType)));
		return llm.complete(model, List.of(question));
	}

	/**
	 * The extensions this tool accepts; readFile sends exactly these here.
	 */
	static Set<String> supportedExtensions() {
		return MIME_TYPES.keySet();
	}
}
