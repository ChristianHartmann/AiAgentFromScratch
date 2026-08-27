package dev.aiengineer.agent.tool;

import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolDefinition;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The tools a model may call, each as definition plus execution. The counterpart of
 * {@code tool_box} and {@code tool_execution} in section 3.3.3.
 *
 * <p>{@link #execute(ToolCall)} never throws: failures go back to the model as text starting
 * with {@code Error: }, so it can react, for example by trying other arguments. In the book
 * every tool catches its own errors, here the toolbox does it once for all tools.
 */
public class Toolbox {

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
		.build();

	private final Map<String, Tool> tools = new LinkedHashMap<>();

	private record Tool(ToolDefinition definition, Function<String, String> executor) {
	}

	public static Toolbox of(Object... toolObjects) {
		Toolbox toolbox = new Toolbox();
		Arrays.stream(toolObjects).forEach(toolbox::addAll);
		return toolbox;
	}

	/**
	 * Adds every public method of the object that is annotated with {@link ToolFunction}.
	 */
	public Toolbox addAll(Object toolObject) {
		Arrays.stream(toolObject.getClass().getMethods())
			.filter(method -> method.isAnnotationPresent(ToolFunction.class))
			.sorted(Comparator.comparing(Method::getName))
			.forEach(method -> add(ToolSchemaGenerator.definitionOf(method),
					arguments -> invoke(toolObject, method, arguments)));
		return this;
	}

	public Toolbox add(ToolDefinition definition, Function<String, String> executor) {
		if (tools.containsKey(definition.name())) {
			throw new IllegalArgumentException("A tool named " + definition.name() + " is already registered");
		}
		tools.put(definition.name(), new Tool(definition, executor));
		return this;
	}

	public List<ToolDefinition> definitions() {
		return tools.values().stream().map(Tool::definition).toList();
	}

	public String execute(ToolCall call) {
		Tool tool = tools.get(call.name());
		if (tool == null) {
			return "Error: unknown tool " + call.name();
		}
		try {
			return tool.executor().apply(call.arguments());
		}
		catch (RuntimeException ex) {
			return "Error: " + messageFor(ex);
		}
	}

	/**
	 * Jackson appends parser locations to its messages, which only add noise for the model.
	 * Its original message keeps what matters, such as the accepted values of an enum.
	 */
	private static String messageFor(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof JacksonException jackson) {
				return jackson.getOriginalMessage();
			}
		}
		return failure.getMessage();
	}

	private static String invoke(Object target, Method method, String arguments) {
		JsonNode values = JSON.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
		Object[] parameters = Arrays.stream(method.getParameters())
			.map(parameter -> argument(method, parameter, values.get(parameter.getName())))
			.toArray();
		try {
			Object result = method.invoke(target, parameters);
			return result instanceof String text ? text : JSON.writeValueAsString(result);
		}
		catch (InvocationTargetException ex) {
			throw ex.getCause() instanceof RuntimeException cause ? cause
					: new IllegalStateException(ex.getCause().getMessage(), ex.getCause());
		}
		catch (IllegalAccessException ex) {
			throw new IllegalStateException("Cannot call " + method.getName(), ex);
		}
	}

	private static Object argument(Method method, Parameter parameter, JsonNode value) {
		if (value == null || value.isNull()) {
			ToolParam param = parameter.getAnnotation(ToolParam.class);
			if (param == null || param.required()) {
				throw new IllegalArgumentException(
						"Missing required argument " + parameter.getName() + " of " + method.getName());
			}
			return null;
		}
		return JSON.convertValue(value, JSON.constructType(parameter.getParameterizedType()));
	}
}
