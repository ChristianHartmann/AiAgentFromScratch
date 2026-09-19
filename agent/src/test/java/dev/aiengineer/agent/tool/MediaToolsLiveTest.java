package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.aiengineer.agent.llm.LiveTests;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.ModelRouter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("llm")
@SpringBootTest(properties = {
	"spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}",
	"spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-set}",
	"spring.ai.google.genai.api-key=${GEMINI_API_KEY:not-set}"
})
class MediaToolsLiveTest {

	@TempDir
	Path root;

	@Autowired
	private LlmClient llm;

	@Autowired
	private ModelRouter router;

	@Value("${agent.media.model}")
	private String model;

	@Test
	void readsTheTextOfAPdf() throws IOException {
		LiveTests.assumeKeyPresentFor(router, model);
		Files.write(root.resolve("note.pdf"), pdfWithText("The secret word is pineapple."));

		String answer = new MediaTools(new Workspace(root), llm, model)
			.readMediaFile("note.pdf", "What is the secret word in this document? Answer with the word only.");

		assertThat(answer).containsIgnoringCase("pineapple");
	}

	/**
	 * A one-page PDF with one line of Helvetica, written by hand so the test needs no PDF
	 * library. The offsets in the cross-reference table are byte positions, hence ASCII only.
	 */
	private static byte[] pdfWithText(String text) {
		String stream = "BT /F1 18 Tf 20 70 Td (" + text + ") Tj ET";
		List<String> objects = Arrays.asList(
				"<< /Type /Catalog /Pages 2 0 R >>",
				"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
				"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 144] /Contents 4 0 R "
						+ "/Resources << /Font << /F1 5 0 R >> >> >>",
				"<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream",
				"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
		StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
		List<Integer> offsets = new ArrayList<>();
		for (int i = 0; i < objects.size(); i++) {
			offsets.add(pdf.length());
			pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
		}
		int xref = pdf.length();
		pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
		offsets.forEach(offset -> pdf.append(String.format("%010d 00000 n \n", offset)));
		pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
			.append(xref).append("\n%%EOF\n");
		return pdf.toString().getBytes(StandardCharsets.US_ASCII);
	}
}
