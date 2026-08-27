package dev.aiengineer.agent.tool;

/**
 * The calculator from section 3.2.1. The book writes its definition by hand, here it comes
 * from the annotations.
 */
public class CalculatorTools {

	public enum Operator {
		ADD, SUBTRACT, MULTIPLY, DIVIDE
	}

	@ToolFunction("Perform basic arithmetic operations.")
	public double calculator(@ToolParam("Arithmetic operation to perform") Operator operator,
			@ToolParam("First number for the calculation") double firstNumber,
			@ToolParam("Second number for the calculation") double secondNumber) {
		return switch (operator) {
			case ADD -> firstNumber + secondNumber;
			case SUBTRACT -> firstNumber - secondNumber;
			case MULTIPLY -> firstNumber * secondNumber;
			case DIVIDE -> {
				if (secondNumber == 0) {
					throw new IllegalArgumentException("Cannot divide by zero");
				}
				yield firstNumber / secondNumber;
			}
		};
	}
}
