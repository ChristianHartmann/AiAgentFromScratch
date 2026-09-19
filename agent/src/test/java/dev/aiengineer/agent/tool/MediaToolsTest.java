package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.aiengineer.agent.llm.Attachment;
import dev.aiengineer.agent.llm.ContentItem;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.Message;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class MediaToolsTest {

	private static final String MODEL = "google/gemini-3.6-flash";

	@TempDir
	Path root;

	private final LlmClient llm = mock(LlmClient.class);

	private MediaTools tools;

	@BeforeEach
	void createTools() {
		tools = new MediaTools(new Workspace(root), llm, MODEL);
	}

	@Test
	void sendsTheFileWithTheQuestionToTheModel() throws IOException {
		Files.write(root.resolve("listing.pdf"), new byte[] { 37, 80, 68, 70 });
		when(llm.complete(eq(MODEL), anyList())).thenReturn("A job listing for a biologist");

		String answer = tools.readMediaFile("listing.pdf", "Which qualifications are required?");

		assertThat(answer).isEqualTo("A job listing for a biologist");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ContentItem>> messages = ArgumentCaptor.forClass(List.class);
		verify(llm).complete(eq(MODEL), messages.capture());
		assertThat(messages.getValue()).singleElement().isInstanceOfSatisfying(Message.class, message -> {
			assertThat(message.content()).isEqualTo("Which qualifications are required?");
			assertThat(message.attachments())
				.containsExactly(new Attachment(new byte[] { 37, 80, 68, 70 }, "application/pdf"));
		});
	}

	@Test
	void choosesTheMimeTypeByExtension() throws IOException {
		Files.write(root.resolve("photo.JPG"), new byte[] { 1 });
		Files.write(root.resolve("voice.mp3"), new byte[] { 2 });
		when(llm.complete(eq(MODEL), anyList())).thenReturn("ok");

		tools.readMediaFile("photo.JPG", "What is shown?");
		tools.readMediaFile("voice.mp3", "What is said?");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ContentItem>> messages = ArgumentCaptor.forClass(List.class);
		verify(llm, times(2)).complete(eq(MODEL), messages.capture());
		assertThat(messages.getAllValues())
			.extracting(list -> ((Message) list.getFirst()).attachments().getFirst().mimeType())
			.containsExactly("image/jpeg", "audio/mpeg");
	}

	@Test
	void refusesFilesItCannotSend() throws IOException {
		Files.writeString(root.resolve("notes.txt"), "text");

		assertThatThrownBy(() -> tools.readMediaFile("notes.txt", "?")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("readFile");
	}

	@Test
	void refusesPathsOutsideTheWorkspace() {
		assertThatThrownBy(() -> tools.readMediaFile("../photo.png", "?"))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outside the workspace");
	}

	@Test
	void coversEveryExtensionThatReadFileSendsHere() {
		assertThat(MediaTools.supportedExtensions()).isEqualTo(FileTools.MEDIA_EXTENSIONS);
	}
}
