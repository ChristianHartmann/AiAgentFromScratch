package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileToolsTest {

	@TempDir
	Path root;

	private FileTools tools;

	@BeforeEach
	void createTools() {
		tools = new FileTools(new Workspace(root));
	}

	@Test
	void unzipsNextToTheArchiveAndListsTheFiles() throws IOException {
		zip("job.zip", "Job Listing.txt", "Applicants.csv");

		String result = tools.unzipFile("job.zip", null);

		assertThat(result).startsWith("Extracted 2 files to job:").contains("job/Job Listing.txt");
		assertThat(root.resolve("job/Applicants.csv")).exists();
	}

	@Test
	void unzipsIntoAGivenFolder() throws IOException {
		zip("job.zip", "a.txt");

		tools.unzipFile("job.zip", "extracted");

		assertThat(root.resolve("extracted/a.txt")).exists();
	}

	@Test
	void namesOnlyTheFirstTwentyExtractedFiles() throws IOException {
		zip("many.zip", IntStream.range(0, 25).mapToObj(i -> "f" + i + ".txt").toArray(String[]::new));

		assertThat(tools.unzipFile("many.zip", null)).endsWith("... and 5 more files");
	}

	@Test
	void refusesAZipEntryThatLeavesTheTargetFolderBeforeExtractingAnything() throws IOException {
		zip("evil.zip", "good.txt", "../evil.txt");

		assertThatThrownBy(() -> tools.unzipFile("evil.zip", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("../evil.txt");
		assertThat(root.resolve("evil.txt")).doesNotExist();
		assertThat(root.resolve("evil/good.txt")).doesNotExist();
	}

	@Test
	void listsFoldersFirstAndFilesWithTheirSize() throws IOException {
		Files.createDirectory(root.resolve("docs"));
		Files.writeString(root.resolve("b.txt"), "12345");
		Files.writeString(root.resolve(".hidden"), "x");

		assertThat(tools.listFiles(null)).isEqualTo("docs/\nb.txt (5 bytes)");
	}

	@Test
	void readsTextWithLineNumbers() throws IOException {
		Files.writeString(root.resolve("notes.md"), "first\nsecond\nthird\n");

		assertThat(tools.readFile("notes.md", null, null)).isEqualTo("   1 | first\n   2 | second\n   3 | third");
	}

	@Test
	void readsARangeOfLines() throws IOException {
		Files.writeString(root.resolve("notes.md"), "first\nsecond\nthird\n");

		assertThat(tools.readFile("notes.md", 2, 2)).isEqualTo("   2 | second");
		assertThat(tools.readFile("notes.md", 5, null)).isEqualTo("The file has only 3 lines");
	}

	@Test
	void cutsLongFilesAndTellsWhereToGoOn() throws IOException {
		Files.writeString(root.resolve("log.txt"), "x".repeat(99).concat("\n").repeat(500));

		String result = tools.readFile("log.txt", null, null);

		assertThat(result).hasSizeLessThan(FileTools.MAX_CHARS + 200).endsWith("Read on with startLine=187.]");
	}

	@Test
	void readsCsvAsTable() throws IOException {
		Files.writeString(root.resolve("sales.csv"), "month,sales\nJanuary,10\n");

		assertThat(tools.readFile("sales.csv", null, null))
			.isEqualTo("   1 | | month | sales |\n   2 | | --- | --- |\n   3 | | January | 10 |");
	}

	@Test
	void readsEverySheetOfAnXlsxFileWithFormulaValues() throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			fill(workbook.createSheet("Applicants"), "Name", "Years");
			Sheet totals = workbook.createSheet("Totals");
			totals.createRow(0).createCell(0).setCellFormula("1+1");
			workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
			write(workbook, "applicants.xlsx");
		}

		assertThat(tools.readFile("applicants.xlsx", null, null))
			.contains("Sheet: Applicants").contains("| Name | Years |")
			.contains("Sheet: Totals").contains("| 2 |");
	}

	@Test
	void readsTheOldXlsFormat() throws IOException {
		try (HSSFWorkbook workbook = new HSSFWorkbook()) {
			fill(workbook.createSheet("Food"), "Item", "Category");
			write(workbook, "food.xls");
		}

		assertThat(tools.readFile("food.xls", null, null)).contains("| Item | Category |");
	}

	@Test
	void readsWordDocuments() throws IOException {
		try (XWPFDocument document = new XWPFDocument(); OutputStream out = Files.newOutputStream(root.resolve("cv.docx"))) {
			document.createParagraph().createRun().setText("Master's degree in biology");
			document.write(out);
		}

		assertThat(tools.readFile("cv.docx", null, null)).contains("Master's degree in biology");
	}

	@Test
	void readsPowerPointSlides() throws IOException {
		try (XMLSlideShow slides = new XMLSlideShow(); OutputStream out = Files.newOutputStream(root.resolve("talk.pptx"))) {
			XSLFTextBox box = slides.createSlide().createTextBox();
			box.setText("Quarterly results");
			slides.write(out);
		}

		assertThat(tools.readFile("talk.pptx", null, null)).contains("Quarterly results");
	}

	@Test
	void sendsPdfsAndMediaToReadMediaFile() throws IOException {
		Files.write(root.resolve("listing.pdf"), new byte[] { 37, 80, 68, 70 });

		assertThatThrownBy(() -> tools.readFile("listing.pdf", null, null))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("readMediaFile");
	}

	@Test
	void refusesBinaryFilesInsteadOfReturningGarbage() throws IOException {
		Files.write(root.resolve("data.bin"), new byte[] { (byte) 0xC3, (byte) 0x28, 0, 1, 2 });

		assertThatThrownBy(() -> tools.readFile("data.bin", null, null))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a UTF-8 text file");
	}

	@Test
	void refusesPathsOutsideTheWorkspace() {
		assertThatThrownBy(() -> tools.readFile("../secret.txt", null, null))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outside the workspace");
	}

	private void zip(String name, String... entries) throws IOException {
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(root.resolve(name)))) {
			for (String entry : entries) {
				zip.putNextEntry(new ZipEntry(entry));
				zip.write(("content of " + entry).getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
	}

	private static void fill(Sheet sheet, String... header) {
		Row row = sheet.createRow(0);
		for (int i = 0; i < header.length; i++) {
			row.createCell(i).setCellValue(header[i]);
		}
	}

	private void write(Workbook workbook, String name) throws IOException {
		try (OutputStream out = Files.newOutputStream(root.resolve(name))) {
			workbook.write(out);
		}
	}
}
