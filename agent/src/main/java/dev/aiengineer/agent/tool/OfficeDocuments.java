package dev.aiengineer.agent.tool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Text of Office files through Apache POI: workbooks as Markdown tables, every sheet with
 * its name (the book reads only the first sheet), Word and PowerPoint as plain text (the book
 * fails on both).
 */
final class OfficeDocuments {

	private OfficeDocuments() {
	}

	/**
	 * Cells as Excel shows them; formulas with the value saved in the file, not the formula.
	 */
	static String workbook(Path file) throws IOException {
		DataFormatter formatter = new DataFormatter();
		formatter.setUseCachedValuesForFormulaCells(true);
		StringBuilder text = new StringBuilder();
		try (Workbook workbook = WorkbookFactory.create(file.toFile(), null, true)) {
			for (Sheet sheet : workbook) {
				List<List<String>> rows = new ArrayList<>();
				for (Row row : sheet) {
					List<String> cells = new ArrayList<>();
					for (int column = 0; column < row.getLastCellNum(); column++) {
						cells.add(formatter.formatCellValue(row.getCell(column)));
					}
					rows.add(cells);
				}
				if (!text.isEmpty()) {
					text.append("\n\n");
				}
				text.append("Sheet: ").append(sheet.getSheetName()).append('\n').append(MarkdownTable.of(rows));
			}
		}
		return text.toString();
	}

	static String word(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file);
				XWPFWordExtractor extractor = new XWPFWordExtractor(new XWPFDocument(in))) {
			return extractor.getText();
		}
	}

	static String slides(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file);
				SlideShowExtractor<XSLFShape, XSLFTextParagraph> extractor = new SlideShowExtractor<>(new XMLSlideShow(in))) {
			return extractor.getText();
		}
	}
}
