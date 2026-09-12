package dev.aiengineer.agent.agent;

import dev.aiengineer.agent.context.Event;
import dev.aiengineer.agent.context.ExecutionContext;
import dev.aiengineer.agent.llm.ContentItem;
import dev.aiengineer.agent.llm.LlmClient;
import dev.aiengineer.agent.llm.LlmRequest;
import dev.aiengineer.agent.llm.LlmResponse;
import dev.aiengineer.agent.llm.Message;
import dev.aiengineer.agent.llm.Role;
import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.llm.ToolChoice;
import dev.aiengineer.agent.llm.ToolResult;
import dev.aiengineer.agent.tool.Tool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The ReAct agent of section 4.6: think, act, observe, repeat. Tools run one after another;
 * a failing or unknown tool becomes an error result the model sees in the next step. A
 * failing model call ends the run with status ERROR instead of looping on silently.
 */
public final class Agent<T> {

	public static final int DEFAULT_MAX_STEPS = 10;

	private final LlmClient llm;

	private final String model;

	private final String name;

	private final String instructions;

	private final Map<String, Tool> tools;

	private final int maxSteps;

	private final Class<T> outputType;

	private Agent(Builder<T> builder) {
		this.llm = builder.llm;
		this.model = Objects.requireNonNull(builder.model, "model");
		this.name = builder.name;
		this.instructions = builder.instructions;
		this.outputType = builder.outputType;
		List<Tool> allTools = new ArrayList<>(builder.tools);
		if (outputType != null) {
			allTools.add(new FinalAnswerTool<>(outputType));
		}
		this.tools = byName(allTools);
		this.maxSteps = builder.maxSteps;
	}

	public static Builder<String> builder(LlmClient llm) {
		return new Builder<>(llm);
	}

	public String name() {
		return name;
	}

	public AgentResult<T> run(String userInput) {
		return run(userInput, new ExecutionContext());
	}

	/**
	 * Runs on an existing context, for example to continue a conversation. A new input asks
	 * for a new answer, so the result of an earlier run is dropped, and every run gets the
	 * full step budget. Without input, the run only goes on where the context stopped.
	 */
	public AgentResult<T> run(String userInput, ExecutionContext context) {
		if (userInput != null) {
			context.finalResult(null);
			context.addEvent(Event.of(context.executionId(), "user", List.of(ContentItem.user(userInput))));
		}
		int stepLimit = context.currentStep() + maxSteps;
		while (!context.hasFinalResult() && context.currentStep() < stepLimit) {
			try {
				step(context);
			}
			catch (RuntimeException ex) {
				return new AgentResult<>(AgentResult.Status.ERROR, null, context, String.valueOf(ex.getMessage()));
			}
			Event last = context.events().getLast();
			if (isFinal(last)) {
				context.finalResult(extractResult(last));
			}
		}
		return context.hasFinalResult()
				? new AgentResult<>(AgentResult.Status.COMPLETE, output(context), context, null)
				: new AgentResult<>(AgentResult.Status.MAX_STEPS, null, context, null);
	}

	/**
	 * One think-act cycle: ask the model, record its answer, run the tools it called and
	 * record their results.
	 */
	public void step(ExecutionContext context) {
		LlmResponse response = think(prepareLlmRequest(context));
		context.addEvent(Event.of(context.executionId(), name, response.content()));
		List<ToolCall> toolCalls = response.toolCalls();
		if (!toolCalls.isEmpty()) {
			context.addEvent(Event.of(context.executionId(), name, act(context, toolCalls)));
		}
		context.incrementStep();
	}

	/**
	 * Builds the request from the context. For now every event goes to the model; this is
	 * the place where chapter 6 selects, compacts and summarizes.
	 */
	public LlmRequest prepareLlmRequest(ExecutionContext context) {
		LlmRequest request = new LlmRequest(model);
		if (!instructions.isBlank()) {
			request.instructions().add(instructions);
		}
		context.events().forEach(event -> request.contents().addAll(event.content()));
		tools.values().forEach(tool -> request.tools().add(tool.definition()));
		request.toolChoice(outputType != null ? ToolChoice.REQUIRED : tools.isEmpty() ? null : ToolChoice.AUTO);
		return request;
	}

	public LlmResponse think(LlmRequest request) {
		return llm.generate(request);
	}

	/**
	 * Runs every tool call in order and returns one result per call, with the id of the call.
	 */
	public List<ToolResult> act(ExecutionContext context, List<ToolCall> toolCalls) {
		List<ToolResult> results = new ArrayList<>();
		for (ToolCall call : toolCalls) {
			Tool tool = tools.get(call.name());
			if (tool == null) {
				results.add(ToolResult.error(call, "Tool '" + call.name() + "' not found"));
				continue;
			}
			try {
				results.add(ToolResult.success(call, tool.execute(context, call.arguments())));
			}
			catch (Exception ex) {
				results.add(ToolResult.error(call, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
			}
		}
		return results;
	}

	private boolean isFinal(Event event) {
		if (outputType != null) {
			return event.content().stream().anyMatch(this::isSuccessfulFinalAnswer);
		}
		return event.content().stream().noneMatch(item -> item instanceof ToolCall || item instanceof ToolResult);
	}

	private Object extractResult(Event event) {
		if (outputType != null) {
			return event.content().stream()
				.filter(this::isSuccessfulFinalAnswer)
				.map(item -> ((ToolResult) item).content())
				.findFirst()
				.orElseThrow();
		}
		return event.content().stream()
			.filter(Message.class::isInstance)
			.map(Message.class::cast)
			.filter(message -> message.role() == Role.ASSISTANT)
			.map(Message::content)
			.findFirst()
			.orElse("");
	}

	private boolean isSuccessfulFinalAnswer(ContentItem item) {
		return item instanceof ToolResult result && result.name().equals(FinalAnswerTool.NAME)
				&& result.status() == ToolResult.Status.SUCCESS;
	}

	@SuppressWarnings("unchecked")
	private T output(ExecutionContext context) {
		return (T) context.finalResult();
	}

	private static Map<String, Tool> byName(List<Tool> tools) {
		Map<String, Tool> byName = new LinkedHashMap<>();
		for (Tool tool : tools) {
			if (byName.putIfAbsent(tool.name(), tool) != null) {
				throw new IllegalArgumentException("Two tools are named " + tool.name());
			}
		}
		return byName;
	}

	public static final class Builder<T> {

		private final LlmClient llm;

		private String model;

		private String name = "agent";

		private String instructions = "";

		private List<Tool> tools = List.of();

		private int maxSteps = DEFAULT_MAX_STEPS;

		private Class<T> outputType;

		private Builder(LlmClient llm) {
			this.llm = Objects.requireNonNull(llm, "llm");
		}

		private Builder(Builder<?> other, Class<T> outputType) {
			this.llm = other.llm;
			this.model = other.model;
			this.name = other.name;
			this.instructions = other.instructions;
			this.tools = other.tools;
			this.maxSteps = other.maxSteps;
			this.outputType = outputType;
		}

		/**
		 * The run ends with a record of this type instead of text, through the final_answer tool.
		 */
		public <R> Builder<R> outputType(Class<R> outputType) {
			return new Builder<>(this, Objects.requireNonNull(outputType, "outputType"));
		}

		public Builder<T> model(String model) {
			this.model = model;
			return this;
		}

		public Builder<T> name(String name) {
			this.name = name;
			return this;
		}

		public Builder<T> instructions(String instructions) {
			this.instructions = instructions;
			return this;
		}

		public Builder<T> tools(List<Tool> tools) {
			this.tools = List.copyOf(tools);
			return this;
		}

		public Builder<T> maxSteps(int maxSteps) {
			this.maxSteps = maxSteps;
			return this;
		}

		public Agent<T> build() {
			return new Agent<>(this);
		}
	}
}
