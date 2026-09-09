package dev.aiengineer.agent.agent;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ToolDefinition;
import dev.aiengineer.agent.tool.Tool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The tool that turns the final answer into a typed result, section 4.7: its input schema is
 * the output schema, wrapped in an "output" property as in the book's repository. Unlike the
 * repository, nested definitions ($defs) stay, so nested records keep a valid schema.
 */
public final class FinalAnswerTool<T> implements Tool {

	public static final String NAME = "final_answer";

	private static final String DESCRIPTION = "Return the final structured answer matching the required schema.";

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
		.build();

	private final Class<T> outputType;

	private final ToolDefinition definition;

	public FinalAnswerTool(Class<T> outputType) {
		this.outputType = outputType;
		Map<String, Object> outputSchema = new LinkedHashMap<>(new BeanOutputConverter<>(outputType).getJsonSchemaMap());
		outputSchema.remove("$schema");
		Map<String, Object> schema = Map.of(
				"type", "object",
				"properties", Map.of("output", outputSchema),
				"required", List.of("output"));
		this.definition = new ToolDefinition(NAME, DESCRIPTION, JSON.writeValueAsString(schema));
	}

	@Override
	public ToolDefinition definition() {
		return definition;
	}

	@Override
	public T execute(ExecutionContext context, String arguments) {
		JsonNode output;
		try {
			output = JSON.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments).get("output");
		}
		catch (JacksonException ex) {
			throw new IllegalArgumentException(ex.getOriginalMessage(), ex);
		}
		if (output == null || output.isNull()) {
			throw new IllegalArgumentException("final_answer needs the answer in the property \"output\"");
		}
		try {
			return JSON.convertValue(output, outputType);
		}
		catch (JacksonException ex) {
			throw new IllegalArgumentException(ex.getOriginalMessage(), ex);
		}
	}
}
