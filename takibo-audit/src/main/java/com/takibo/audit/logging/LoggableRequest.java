package com.takibo.audit.logging;
import java.lang.annotation.*;

/**
 * Annotation pour marquer les classes dont les appels doivent être logués de manière essentielle.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoggableRequest {
}
