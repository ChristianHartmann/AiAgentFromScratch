package dev.aiengineer.agent.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a parameter of a tool. Optional parameters receive {@code null} when the model
 * leaves them out, so they have to use wrapper types.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {

	String value() default "";

	boolean required() default true;
}
