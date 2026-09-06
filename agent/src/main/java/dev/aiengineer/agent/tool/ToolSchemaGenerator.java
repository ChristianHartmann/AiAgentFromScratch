package dev.aiengineer.agent.tool;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ToolDefinition;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Derives the tool definition of a method: name, description and the JSON schema of its
 * parameters. The counterpart of {@code function_to_tool_definition} in section 3.3.2.
 *
 * <p>Unlike the book, an unsupported parameter type is an error instead of silently becoming
 * a string: a schema that does not match the method only fails later, at the model.
 */
public final class ToolSchemaGenerator {

	private static final Map<Class<?>, String> JSON_TYPES = Map.ofEntries(
			Map.entry(String.class, "string"),
			Map.entry(int.class, "integer"), Map.entry(Integer.class, "integer"),
			Map.entry(long.class, "integer"), Map.entry(Long.class, "integer"),
			Map.entry(double.class, "number"), Map.entry(Double.class, "number"),
			Map.entry(float.class, "number"), Map.entry(Float.class, "number"),
			Map.entry(boolean.class, "boolean"), Map.entry(Boolean.class, "boolean"));

	private ToolSchemaGenerator() {
	}

	public static ToolDefinition definitionOf(Method method) {
		ToolFunction function = method.getAnnotation(ToolFunction.class);
		if (function == null) {
			throw new IllegalArgumentException(method.getName() + " is not annotated with @ToolFunction");
		}
		JsonMapper mapper = JsonMapper.shared();
		ObjectNode schema = mapper.createObjectNode();
		schema.put("type", "object");
		ObjectNode properties = schema.putObject("properties");
		ArrayNode required = schema.putArray("required");
		for (Parameter parameter : method.getParameters()) {
			if (ExecutionContext.class.equals(parameter.getType())) {
				continue;
			}
			if (!parameter.isNamePresent()) {
				throw new IllegalStateException("Parameter names of " + method.getName()
						+ " are missing from the bytecode, compile with -parameters");
			}
			ToolParam param = parameter.getAnnotation(ToolParam.class);
			boolean isRequired = param == null || param.required();
			if (!isRequired && parameter.getType().isPrimitive()) {
				throw new IllegalArgumentException("Optional parameter " + parameter.getName() + " of "
						+ method.getName() + " must use a wrapper type, a primitive cannot be null");
			}
			ObjectNode property = properties.putObject(parameter.getName());
			describe(method, parameter, parameter.getParameterizedType(), property);
			if (param != null && !param.value().isBlank()) {
				property.put("description", param.value());
			}
			if (isRequired) {
				required.add(parameter.getName());
			}
		}
		return new ToolDefinition(method.getName(), function.value(), mapper.writeValueAsString(schema));
	}

	private static void describe(Method method, Parameter parameter, Type type, ObjectNode property) {
		Class<?> rawType = type instanceof ParameterizedType parameterized
				? (Class<?>) parameterized.getRawType() : (Class<?>) type;
		if (JSON_TYPES.containsKey(rawType)) {
			property.put("type", JSON_TYPES.get(rawType));
		}
		else if (rawType.isEnum()) {
			property.put("type", "string");
			ArrayNode values = property.putArray("enum");
			for (Object constant : rawType.getEnumConstants()) {
				values.add(((Enum<?>) constant).name());
			}
		}
		else if (List.class.equals(rawType) && type instanceof ParameterizedType parameterized) {
			property.put("type", "array");
			describe(method, parameter, parameterized.getActualTypeArguments()[0], property.putObject("items"));
		}
		else {
			throw new IllegalArgumentException("Unsupported type " + rawType.getSimpleName() + " of parameter "
					+ parameter.getName() + " in " + method.getName());
		}
	}
}
