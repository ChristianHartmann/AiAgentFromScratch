package dev.aiengineer.agent.callback;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Asks on the console, as input() in Listing 5.27. Blocks until someone answers, so it only
 * suits programs with a person at the terminal; chapter 6 pauses the run instead.
 */
public class ConsoleApprover implements Approver {

	private static final Set<String> YES = Set.of("y", "yes");

	private final BufferedReader in;

	private final PrintStream out;

	public ConsoleApprover() {
		this(System.in, System.out);
	}

	public ConsoleApprover(InputStream in, PrintStream out) {
		this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		this.out = out;
	}

	@Override
	public boolean approve(String toolName, String arguments) {
		out.println("The agent wants to run " + toolName + " with " + arguments);
		out.print("Do you want to execute? (y/n): ");
		out.flush();
		try {
			String answer = in.readLine();
			return answer != null && YES.contains(answer.strip().toLowerCase(Locale.ROOT));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
