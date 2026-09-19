package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceTest {

	@TempDir
	Path root;

	@Test
	void resolvesPathsRelativeToTheRoot() {
		Workspace workspace = new Workspace(root);

		assertThat(workspace.resolve("a/b.txt")).isEqualTo(root.resolve("a/b.txt"));
		assertThat(workspace.resolve(null)).isEqualTo(root);
		assertThat(workspace.resolve("")).isEqualTo(root);
	}

	@Test
	void rejectsPathsOutsideTheRoot() {
		Workspace workspace = new Workspace(root);

		assertThatThrownBy(() -> workspace.resolve("../outside.txt")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("outside the workspace");
		assertThatThrownBy(() -> workspace.resolve("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> workspace.resolve("a/../../x")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void namesPathsRelativeToTheRoot() {
		Workspace workspace = new Workspace(root);

		assertThat(workspace.relative(root.resolve("a/b.txt"))).isEqualTo("a/b.txt");
		assertThat(workspace.relative(root)).isEqualTo(".");
	}
}
