package dev.aiengineer.agent.context;

import static org.assertj.core.api.Assertions.assertThat;

import dev.aiengineer.agent.llm.ContentItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionContextTest {

	private final ExecutionContext context = new ExecutionContext();

	@Test
	void startsEmptyWithAnExecutionId() {
		assertThat(context.executionId()).isNotBlank();
		assertThat(context.events()).isEmpty();
		assertThat(context.currentStep()).isZero();
		assertThat(context.hasFinalResult()).isFalse();
	}

	@Test
	void keepsEventsInTheOrderTheyWereAdded() {
		context.addEvent(Event.of(context.executionId(), "user", List.of(ContentItem.user("first"))));
		context.addEvent(Event.of(context.executionId(), "agent", List.of(ContentItem.assistant("second"))));

		assertThat(context.events()).extracting(Event::author).containsExactly("user", "agent");
	}

	@Test
	void returnsASnapshotOfTheEvents() {
		context.addEvent(Event.of(context.executionId(), "user", List.of(ContentItem.user("first"))));
		List<Event> snapshot = context.events();

		context.addEvent(Event.of(context.executionId(), "agent", List.of(ContentItem.assistant("second"))));

		assertThat(snapshot).hasSize(1);
	}

	@Test
	void givesEveryEventItsOwnIdAndTheExecutionId() {
		Event first = Event.of(context.executionId(), "user", List.of(ContentItem.user("a")));
		Event second = Event.of(context.executionId(), "user", List.of(ContentItem.user("b")));

		assertThat(first.id()).isNotEqualTo(second.id());
		assertThat(first.executionId()).isEqualTo(context.executionId());
		assertThat(first.timestamp()).isNotNull();
	}

	@Test
	void countsSteps() {
		context.incrementStep();
		context.incrementStep();

		assertThat(context.currentStep()).isEqualTo(2);
	}

	@Test
	void anEmptyStringIsAFinalResult() {
		context.finalResult("");

		assertThat(context.hasFinalResult()).isTrue();
	}

	@Test
	void offersAStateForTools() {
		context.state().put("counter", 1);

		assertThat(context.state()).containsEntry("counter", 1);
	}
}
