package dev.aiengineer.agent.tool;

/**
 * The stand-in of Listing 5.26 for trying the approval: it checks the path but deletes
 * nothing.
 */
public class DeleteFileTools {

	private final Workspace workspace;

	public DeleteFileTools(Workspace workspace) {
		this.workspace = workspace;
	}

	@ToolFunction(value = "Delete a file in the workspace.", requiresConfirmation = true)
	public String deleteFile(@ToolParam("Path of the file, relative to the workspace") String filePath) {
		return "Deleted " + workspace.relative(workspace.resolve(filePath));
	}
}
