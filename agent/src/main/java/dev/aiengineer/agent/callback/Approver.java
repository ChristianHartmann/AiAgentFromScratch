package dev.aiengineer.agent.callback;

/**
 * Decides whether a tool may run, for the approval of section 5.5.3.
 */
@FunctionalInterface
public interface Approver {

	boolean approve(String toolName, String arguments);
}
