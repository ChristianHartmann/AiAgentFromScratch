package dev.aiengineer.agent.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central storage of a run, section 4.3.2: every event in order, the step counter, a state
 * for tools, and the final result. Events and state sit behind methods so that chapter 6 can
 * move them into a session. Belongs to exactly one run and is not thread safe.
 */
public final class ExecutionContext {

	private final String executionId = UUID.randomUUID().toString();

	private final List<Event> events = new ArrayList<>();

	private final Map<String, Object> state = new HashMap<>();

	private int currentStep;

	private Object finalResult;

	public String executionId() {
		return executionId;
	}

	public List<Event> events() {
		return List.copyOf(events);
	}

	public void addEvent(Event event) {
		events.add(event);
	}

	public int currentStep() {
		return currentStep;
	}

	public void incrementStep() {
		currentStep++;
	}

	public Map<String, Object> state() {
		return state;
	}

	public Object finalResult() {
		return finalResult;
	}

	public void finalResult(Object finalResult) {
		this.finalResult = finalResult;
	}

	public boolean hasFinalResult() {
		return finalResult != null;
	}
}
