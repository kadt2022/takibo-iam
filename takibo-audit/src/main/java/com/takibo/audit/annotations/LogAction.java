package com.takibo.audit.annotations;

import org.springframework.boot.logging.LogLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogAction {
    String value() default "";
    LogLevel level() default LogLevel.INFO;
    boolean trackParams() default true;
}