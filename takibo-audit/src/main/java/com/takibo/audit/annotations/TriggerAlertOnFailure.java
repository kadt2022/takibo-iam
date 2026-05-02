package com.takibo.audit.annotations;

import com.takibo.audit.domain.AuditType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TriggerAlertOnFailure {
    Class<? extends Exception>[] triggerOn() default {};  // Renommer value en triggerOn
    int threshold() default 1;
    AuditType auditType() default AuditType.SECURITY;
}