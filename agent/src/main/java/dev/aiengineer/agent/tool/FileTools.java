package dev.aiengineer.agent.tool;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The file tools of section 5.4.2: unzip, list and read files in a workspace. Formats the
 * book cannot read (docx, pptx, every sheet of a workbook) are read here; PDF, images and
 * audio go to readMediaFile in MediaTools.
 */
public class FileTools {

	static final int MAX_CHARS = 20_000;

	static final Set<String> MEDIA_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "gif", "webp", "bmp", "mp3",
			"wav", "m4a", "flac", "ogg", "webm");

	private static final int MAX_LISTED_FILES = 20;

	private final Workspace workspace;

	public FileTools(Workspace workspace) {
		this.workspace = workspace;
	}

	/**
	 * Checks every entry before extracting any: in Java, unlike Python's extractall, an entry
	 * like ../x would otherwise be written outside the target folder (zip slip).
	 */
	@ToolFunction("Extract a zip archive in the workspace. Returns the extracted files.")
	public String unzipFile(@ToolParam("Path of the zip file, relative to the workspace") String zipPath,
			@ToolParam(value = "Folder to extract to, relative to the workspace; a folder named like the archive "
					+ "next to it if omitted", required = false) String extractTo) throws IOException {
		Path archive = workspace.resolve(zipPath);
		Path target = extractTo == null || extractTo.isBlank()
				? archive.resolveSibling(withoutExtension(archive.getFileName().toString()))
				: workspace.resolve(extractTo);
		List<String> extracted = new ArrayList<>();
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			List<? extends ZipEntry> entries = zip.stream().toList();
			for (ZipEntry entry : entries) {
				if (!target.resolve(entry.getName()).normalize().startsWith(target)) {
					throw new IllegalArgumentException(
							"Zip entry " + entry.getName() + " would be extracted outside " + workspace.relative(target));
				}
			}
			for (ZipEntry entry : entries) {
				Path destination = target.resolve(entry.getName()).normalize();
				if (entry.isDirectory()) {
					Files.createDirectories(destination);
					continue;
				}
				Files.createDirectories(destination.getParent());
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
				}
				extracted.add(workspace.relative(destination));
			}
		}
		StringBuilder text = new StringBuilder("Extracted " + extracted.size() + " files to "
				+ workspace.relative(target) + ":");
		extracted.stream().limit(MAX_LISTED_FILES).forEach(file -> text.append('\n').append(file));
		if (extracted.size() > MAX_LISTED_FILES) {
			text.append("\n... and ").append(extracted.size() - MAX_LISTED_FILES).append(" more files");
		}
		return text.toString();
	}

	@ToolFunction("List the files and folders in a folder of the workspace, folders first, files with their size.")
	public String listFiles(@ToolParam(value = "Folder relative to the workspace, the workspace itself if omitted",
			required = false) String path) throws IOException {
		Path folder = workspace.resolve(path);
		if (!Files.isDirectory(folder)) {
			throw new IllegalArgumentException(workspace.relative(folder) + " is not a folder");
		}
		List<Path> entries;
		try (Stream<Path> stream = Files.list(folder)) {
			entries = stream.filter(entry -> !entry.getFileName().toString().startsWith("."))
				.sorted(Comparator.comparing((Path entry) -> !Files.isDirectory(entry))
					.thenComparing(entry -> entry.getFileName().toString()))
				.toList();
		}
		if (entries.isEmpty()) {
			return workspace.relative(folder) + " is empty";
		}
		return entries.stream()
			.map(entry -> Files.isDirectory(entry) ? entry.getFileName() + "/"
					: entry.getFileName() + " (" + sizeOf(entry) + " bytes)")
			.collect(Collectors.joining("\n"));
	}

	@ToolFunction("Read a file of the workspace with line numbers: text, CSV, Excel (every sheet), Word and "
			+ "PowerPoint. Long files come in parts; use startLine and endLine for the rest. For PDF, images and "
			+ "audio use readMediaFile.")
	public String readFile(@ToolParam("Path of the file, relative to the workspace") String filePath,
			@ToolParam(value = "First line to read, 1 if omitted", required = false) Integer startLine,
			@ToolParam(value = "Last line to read, the end of the file if omitted", required = false) Integer endLine)
			throws IOException {
		Path file = workspace.resolve(filePath);
		if (!Files.isRegularFile(file)) {
			throw new IllegalArgumentException(filePath + " is not a file");
		}
		String extension = extensionOf(file);
		if (MEDIA_EXTENSIONS.contains(extension)) {
			throw new IllegalArgumentException(filePath + " is a PDF, image or audio file, use readMediaFile");
		}
		String content = switch (extension) {
			case "csv" -> MarkdownTable.of(Csv.parse(readText(file)));
			case "xlsx", "xls" -> OfficeDocuments.workbook(file);
			case "docx" -> OfficeDocuments.word(file);
			case "pptx" -> OfficeDocuments.slides(file);
			default -> readText(file);
		};
		return numbered(content, startLine == null ? 1 : startLine, endLine == null ? -1 : endLine);
	}

	/**
	 * Lines in the format of Listing 5.18, at most MAX_CHARS characters: the book puts any file
	 * whole into the context, in the chapter about saving context.
	 */
	static String numbered(String content, int startLine, int endLine) {
		List<String> lines = content.lines().toList();
		if (lines.isEmpty()) {
			return "(empty file)";
		}
		int first = Math.max(1, startLine);
		int last = endLine < 1 ? lines.size() : Math.min(endLine, lines.size());
		if (first > lines.size()) {
			return "The file has only " + lines.size() + " lines";
		}
		StringBuilder text = new StringBuilder();
		for (int number = first; number <= last; number++) {
			String line = String.format("%4d | %s\n", number, lines.get(number - 1));
			if (text.length() + line.length() > MAX_CHARS) {
				if (text.isEmpty()) {
					return line.substring(0, MAX_CHARS) + "\n[Line " + number + " is cut after " + MAX_CHARS
							+ " characters.]";
				}
				return text + "[Truncated after line " + (number - 1) + " of " + lines.size()
						+ ". Read on with startLine=" + number + ".]";
			}
			text.append(line);
		}
		return text.toString().stripTrailing();
	}

	private static String readText(Path file) throws IOException {
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(Files.readAllBytes(file)))
				.toString();
		}
		catch (CharacterCodingException ex) {
			throw new IllegalArgumentException(file.getFileName() + " is not a UTF-8 text file");
		}
	}

	static String extensionOf(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String withoutExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot <= 0 ? name + "_extracted" : name.substring(0, dot);
	}

	private static long sizeOf(Path file) {
		try {
			return Files.size(file);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
