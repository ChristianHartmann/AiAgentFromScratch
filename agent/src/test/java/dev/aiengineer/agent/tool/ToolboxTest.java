package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolboxTest {

	public record Weather(String city, int temperature) {
	}

	public enum Unit {
		CELSIUS, FAHRENHEIT
	}

	public static class SampleTools {

		@ToolFunction("Greets a person.")
		public String greet(String name, @ToolParam(required = false) String greeting) {
			return (greeting == null ? "Hello" : greeting) + " " + name;
		}

		@ToolFunction("Returns the weather.")
		public Weather weather(String city, Unit unit) {
			return new Weather(city, unit == Unit.CELSIUS ? 21 : 70);
		}

		@ToolFunction("Sums numbers.")
		public int sum(List<Integer> numbers) {
			return numbers.stream().mapToInt(Integer::intValue).sum();
		}

		@ToolFunction("Always fails.")
		public String fail() {
			throw new IllegalStateException("Service unavailable");
		}

		public String notATool() {
			return "hidden";
		}
	}

	private final Toolbox toolbox = Toolbox.of(new SampleTools());

	@Test
	void offersEveryAnnotatedMethod() {
		assertThat(toolbox.definitions()).extracting(ToolDefinition::name)
			.containsExactlyInAnyOrder("greet", "weather", "sum", "fail");
	}

	@Test
	void passesTheArgumentsToTheParameters() {
		assertThat(execute("greet", "{\"name\":\"Max\",\"greeting\":\"Hi\"}")).isEqualTo("Hi Max");
	}

	@Test
	void passesNullForAMissingOptionalArgument() {
		assertThat(execute("greet", "{\"name\":\"Max\"}")).isEqualTo("Hello Max");
	}

	@Test
	void returnsObjectsAsJson() {
		assertThat(execute("weather", "{\"city\":\"Berlin\",\"unit\":\"CELSIUS\"}"))
			.isEqualTo("{\"city\":\"Berlin\",\"temperature\":21}");
	}

	@Test
	void acceptsEnumValuesInLowerCase() {
		assertThat(execute("weather", "{\"city\":\"Berlin\",\"unit\":\"celsius\"}")).contains("21");
	}

	@Test
	void readsGenericLists() {
		assertThat(execute("sum", "{\"numbers\":[1,2,3]}")).isEqualTo("6");
	}

	@Test
	void returnsTheMessageOfAFailingToolAsError() {
		assertThat(execute("fail", "{}")).isEqualTo("Error: Service unavailable");
	}

	@Test
	void namesAMissingRequiredArgument() {
		assertThat(execute("greet", "{}")).startsWith("Error:").contains("name");
	}

	@Test
	void reportsBrokenJsonAsError() {
		assertThat(execute("greet", "{name: Max")).startsWith("Error:");
	}

	@Test
	void leavesOutTheParserLocationTheModelCannotUse() {
		assertThat(execute("greet", "{name: Max")).doesNotContain("REDACTED").doesNotContain("byte offset");
	}

	@Test
	void namesTheAcceptedValuesOfAnEnum() {
		assertThat(execute("weather", "{\"city\":\"Berlin\",\"unit\":\"kelvin\"}"))
			.startsWith("Error:")
			.contains("CELSIUS").contains("FAHRENHEIT")
			.doesNotContain("location information");
	}

	@Test
	void reportsAnUnknownTool() {
		assertThat(execute("teleport", "{}")).isEqualTo("Error: unknown tool teleport");
	}

	@Test
	void acceptsToolsWithTheirOwnExecution() {
		Toolbox custom = new Toolbox()
			.add(new ToolDefinition("echo", "Echoes the input", "{\"type\":\"object\"}"), arguments -> arguments);

		assertThat(custom.execute(new ToolCall("1", "echo", "{\"a\":1}"))).isEqualTo("{\"a\":1}");
	}

	@Test
	void rejectsTwoToolsWithTheSameName() {
		ToolDefinition greet = new ToolDefinition("greet", "Duplicate", "{\"type\":\"object\"}");

		assertThatThrownBy(() -> toolbox.add(greet, arguments -> ""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("greet");
	}

	private String execute(String name, String arguments) {
		return toolbox.execute(new ToolCall("call_1", name, arguments));
	}
}
