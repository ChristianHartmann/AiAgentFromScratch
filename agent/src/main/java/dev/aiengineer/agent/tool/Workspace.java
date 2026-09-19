package dev.aiengineer.agent.tool;

import java.nio.file.Path;

/**
 * The folder the file tools work in. Every path the model sends is taken relative to it and
 * must not leave it; the book's tools accept any path on the machine.
 */
public final class Workspace {

	private final Path root;

	public Workspace(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	public Path root() {
		return root;
	}

	public Path resolve(String path) {
		Path resolved = root.resolve(path == null || path.isBlank() ? "." : path).normalize();
		if (!resolved.startsWith(root)) {
			throw new IllegalArgumentException("Path " + path + " is outside the workspace");
		}
		return resolved;
	}

	public String relative(Path path) {
		String relative = root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
		return relative.isEmpty() ? "." : relative;
	}
}
