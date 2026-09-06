package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ToolDefinition;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ToolSchemaGeneratorTest {

	enum Unit {
		CELSIUS, FAHRENHEIT
	}

	static class SampleTools {

		@ToolFunction("Uses every supported type.")
		public String everyType(String text, int count, Long big, double ratio, Float small, boolean flag,
				Boolean switchOn, List<String> tags, Unit unit) {
			return "";
		}

		@ToolFunction("Searches the web.")
		public String search(@ToolParam("The search query") String query,
				@ToolParam(value = "Maximum number of results", required = false) Integer maxResults) {
			return "";
		}

		public String notATool(String text) {
			return text;
		}

		@ToolFunction("Takes a date.")
		public String unsupported(LocalDate date) {
			return "";
		}

		@ToolFunction("Optional primitive.")
		public String optionalPrimitive(@ToolParam(value = "count", required = false) int count) {
			return "";
		}

		@ToolFunction("Uses the context.")
		public String withContext(ExecutionContext context, String query) {
			return "";
		}
	}

	@Test
	void usesMethodNameAndDescription() {
		ToolDefinition definition = ToolSchemaGenerator.definitionOf(method("search"));

		assertThat(definition.name()).isEqualTo("search");
		assertThat(definition.description()).isEqualTo("Searches the web.");
	}

	@Test
	void mapsEveryTypeOfTheTable() {
		JsonNode properties = schema("everyType").path("properties");

		assertThat(properties.path("text").path("type").asString()).isEqualTo("string");
		assertThat(properties.path("count").path("type").asString()).isEqualTo("integer");
		assertThat(properties.path("big").path("type").asString()).isEqualTo("integer");
		assertThat(properties.path("ratio").path("type").asString()).isEqualTo("number");
		assertThat(properties.path("small").path("type").asString()).isEqualTo("number");
		assertThat(properties.path("flag").path("type").asString()).isEqualTo("boolean");
		assertThat(properties.path("switchOn").path("type").asString()).isEqualTo("boolean");
		assertThat(properties.path("tags").path("type").asString()).isEqualTo("array");
		assertThat(properties.path("tags").path("items").path("type").asString()).isEqualTo("string");
		assertThat(properties.path("unit").path("type").asString()).isEqualTo("string");
		assertThat(properties.path("unit").path("enum").values()).extracting(JsonNode::asString)
			.containsExactly("CELSIUS", "FAHRENHEIT");
	}

	@Test
	void describesTheInputAsObject() {
		assertThat(schema("search").path("type").asString()).isEqualTo("object");
	}

	@Test
	void marksParametersAsRequiredUnlessDeclaredOptional() {
		assertThat(schema("search").path("required").values()).extracting(JsonNode::asString)
			.containsExactly("query");
	}

	@Test
	void takesDescriptionsFromToolParam() {
		JsonNode properties = schema("search").path("properties");

		assertThat(properties.path("query").path("description").asString()).isEqualTo("The search query");
		assertThat(properties.path("maxResults").path("description").asString())
			.isEqualTo("Maximum number of results");
	}

	@Test
	void rejectsMethodsWithoutToolFunction() {
		assertThatThrownBy(() -> ToolSchemaGenerator.definitionOf(method("notATool")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("notATool");
	}

	@Test
	void rejectsUnsupportedTypesNamingMethodAndParameter() {
		assertThatThrownBy(() -> ToolSchemaGenerator.definitionOf(method("unsupported")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("unsupported")
			.hasMessageContaining("date")
			.hasMessageContaining("LocalDate");
	}

	@Test
	void rejectsOptionalPrimitivesBecauseTheyCannotBeNull() {
		assertThatThrownBy(() -> ToolSchemaGenerator.definitionOf(method("optionalPrimitive")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("count");
	}

	@Test
	void leavesExecutionContextParametersOut() {
		JsonNode schema = schema("withContext");

		assertThat(schema.path("properties").has("context")).isFalse();
		assertThat(schema.path("required").values()).extracting(JsonNode::asString).containsExactly("query");
	}

	private static Method method(String name) {
		return Arrays.stream(SampleTools.class.getMethods())
			.filter(method -> method.getName().equals(name))
			.findFirst()
			.orElseThrow();
	}

	private static JsonNode schema(String methodName) {
		return JsonMapper.shared().readTree(ToolSchemaGenerator.definitionOf(method(methodName)).inputSchema());
	}
}
