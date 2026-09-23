package dev.aiengineer.agent.tool;

import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ToolDefinition;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * A tool made from an annotated method, section 4.4.3. A parameter of type ExecutionContext
 * receives the context of the run; the book recognizes it by the name "context", Java by type.
 */
public final class FunctionTool implements Tool {

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
		.build();

	private final Object target;

	private final Method method;

	private final ToolDefinition definition;

	private FunctionTool(Object target, Method method) {
		this.target = target;
		this.method = method;
		this.definition = ToolSchemaGenerator.definitionOf(method);
	}

	public static FunctionTool of(Object target, Method method) {
		return new FunctionTool(target, method);
	}

	/**
	 * Every public method of the object that is annotated with {@link ToolFunction}, sorted
	 * by name.
	 */
	public static List<Tool> allOf(Object target) {
		return Arrays.stream(target.getClass().getMethods())
			.filter(method -> method.isAnnotationPresent(ToolFunction.class))
			.sorted(Comparator.comparing(Method::getName))
			.<Tool>map(method -> of(target, method))
			.toList();
	}

	@Override
	public ToolDefinition definition() {
		return definition;
	}

	@Override
	public boolean requiresConfirmation() {
		return method.getAnnotation(ToolFunction.class).requiresConfirmation();
	}

	@Override
	public Object execute(ExecutionContext context, String arguments) throws Exception {
		JsonNode values = parse(arguments);
		Object[] parameters = Arrays.stream(method.getParameters())
			.map(parameter -> ExecutionContext.class.equals(parameter.getType()) ? context
					: argument(parameter, values.get(parameter.getName())))
			.toArray();
		try {
			return method.invoke(target, parameters);
		}
		catch (InvocationTargetException ex) {
			throw ex.getCause() instanceof Exception cause ? cause : ex;
		}
	}

	/**
	 * Jackson appends parser locations to its messages, which only add noise for the model.
	 * Its original message keeps what matters, such as the accepted values of an enum.
	 */
	private static JsonNode parse(String arguments) {
		try {
			return JSON.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
		}
		catch (JacksonException ex) {
			throw new IllegalArgumentException(ex.getOriginalMessage(), ex);
		}
	}

	private Object argument(Parameter parameter, JsonNode value) {
		if (value == null || value.isNull()) {
			ToolParam param = parameter.getAnnotation(ToolParam.class);
			if (param == null || param.required()) {
				throw new IllegalArgumentException(
						"Missing required argument " + parameter.getName() + " of " + method.getName());
			}
			return null;
		}
		try {
			return JSON.convertValue(value, JSON.constructType(parameter.getParameterizedType()));
		}
		catch (JacksonException ex) {
			throw new IllegalArgumentException(ex.getOriginalMessage(), ex);
		}
	}
}
