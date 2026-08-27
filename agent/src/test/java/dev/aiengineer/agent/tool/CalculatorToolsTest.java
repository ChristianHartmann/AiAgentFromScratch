package dev.aiengineer.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.aiengineer.agent.llm.ToolCall;
import dev.aiengineer.agent.tool.CalculatorTools.Operator;
import org.junit.jupiter.api.Test;

class CalculatorToolsTest {

	private final CalculatorTools calculator = new CalculatorTools();

	@Test
	void performsAllFourOperations() {
		assertThat(calculator.calculator(Operator.ADD, 2, 3)).isEqualTo(5);
		assertThat(calculator.calculator(Operator.SUBTRACT, 2, 3)).isEqualTo(-1);
		assertThat(calculator.calculator(Operator.MULTIPLY, 1234, 5678)).isEqualTo(7006652);
		assertThat(calculator.calculator(Operator.DIVIDE, 7, 2)).isEqualTo(3.5);
	}

	@Test
	void refusesToDivideByZero() {
		assertThatThrownBy(() -> calculator.calculator(Operator.DIVIDE, 1, 0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Cannot divide by zero");
	}

	@Test
	void worksAsToolInsideTheToolbox() {
		Toolbox toolbox = Toolbox.of(calculator);

		String result = toolbox.execute(new ToolCall("1", "calculator",
				"{\"operator\":\"multiply\",\"firstNumber\":1234,\"secondNumber\":5678}"));

		assertThat(result).isEqualTo("7006652.0");
	}
}
