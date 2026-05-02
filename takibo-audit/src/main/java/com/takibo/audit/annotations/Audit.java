package com.takibo.audit.annotations;


import com.takibo.audit.domain.AuditType;
import com.takibo.audit.domain.LogLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {
    AuditType type();
    String entityType();

    /**
     * Expression SpEL optionnelle pour résoudre l'ID.
     * Si vide, le système tentera de trouver automatiquement un ID.
     * Variables disponibles: #result, #args
     */
    String entityIdParam() default "";

    /**
     * Niveau de criticité pour le logging
     */
    LogLevel level() default LogLevel.INFO;
}