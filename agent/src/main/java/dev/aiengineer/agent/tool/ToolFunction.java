package dev.aiengineer.agent.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Turns a method into a tool the model can call. The value tells the model what the tool
 * does, so write it for the model, not for a developer.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolFunction {

	String value();
}
