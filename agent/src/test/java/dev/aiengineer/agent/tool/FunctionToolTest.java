package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.aiengineer.agent.context.ExecutionContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class FunctionToolTest {

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

		@ToolFunction("Counts calls in the state of the run.")
		public int count(ExecutionContext context, String label) {
			int count = (int) context.state().merge(label, 1, (a, b) -> (int) a + (int) b);
			return count;
		}

		@ToolFunction(value = "Removes a thing.", requiresConfirmation = true)
		public String remove(String name) {
			return "removed " + name;
		}

		public String notATool() {
			return "hidden";
		}
	}

	private final List<Tool> tools = FunctionTool.allOf(new SampleTools());

	private final ExecutionContext context = new ExecutionContext();

	@Test
	void offersEveryAnnotatedMethodSortedByName() {
		assertThat(tools).extracting(Tool::name).containsExactly("count", "fail", "greet", "remove", "sum", "weather");
	}

	@Test
	void passesTheArgumentsToTheParameters() throws Exception {
		assertThat(execute("greet", "{\"name\":\"Max\",\"greeting\":\"Hi\"}")).isEqualTo("Hi Max");
	}

	@Test
	void passesNullForAMissingOptionalArgument() throws Exception {
		assertThat(execute("greet", "{\"name\":\"Max\"}")).isEqualTo("Hello Max");
	}

	@Test
	void returnsTheResultObjectAsItIs() throws Exception {
		assertThat(execute("weather", "{\"city\":\"Berlin\",\"unit\":\"CELSIUS\"}")).isEqualTo(new Weather("Berlin", 21));
	}

	@Test
	void acceptsEnumValuesInLowerCase() throws Exception {
		assertThat(execute("weather", "{\"city\":\"Berlin\",\"unit\":\"celsius\"}")).isEqualTo(new Weather("Berlin", 21));
	}

	@Test
	void readsGenericLists() throws Exception {
		assertThat(execute("sum", "{\"numbers\":[1,2,3]}")).isEqualTo(6);
	}

	@Test
	void passesTheExecutionContextToAParameterOfItsType() throws Exception {
		execute("count", "{\"label\":\"a\"}");

		assertThat(execute("count", "{\"label\":\"a\"}")).isEqualTo(2);
	}

	@Test
	void leavesTheExecutionContextOutOfTheSchema() {
		assertThat(tool("count").definition().inputSchema()).contains("label").doesNotContain("context");
	}

	@Test
	void throwsTheExceptionOfAFailingTool() {
		assertThatThrownBy(() -> execute("fail", "{}")).isInstanceOf(IllegalStateException.class)
			.hasMessage("Service unavailable");
	}

	@Test
	void namesAMissingRequiredArgument() {
		assertThatThrownBy(() -> execute("greet", "{}")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("name");
	}

	@Test
	void reportsBrokenJsonWithoutParserLocationNoise() {
		assertThatThrownBy(() -> execute("greet", "{name: Max"))
			.isInstanceOf(IllegalArgumentException.class)
			.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("REDACTED").doesNotContain("byte offset"));
	}

	@Test
	void namesTheAcceptedValuesOfAnEnum() {
		assertThatThrownBy(() -> execute("weather", "{\"city\":\"Berlin\",\"unit\":\"kelvin\"}"))
			.isInstanceOf(IllegalArgumentException.class)
			.satisfies(ex -> assertThat(ex.getMessage()).contains("CELSIUS").contains("FAHRENHEIT")
				.doesNotContain("location information"));
	}

	@Test
	void takesTheNeedForConfirmationFromTheAnnotation() {
		assertThat(tool("remove").requiresConfirmation()).isTrue();
		assertThat(tool("greet").requiresConfirmation()).isFalse();
	}

	private Object execute(String name, String arguments) throws Exception {
		return tool(name).execute(context, arguments);
	}

	private Tool tool(String name) {
		return tools.stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
	}
}
