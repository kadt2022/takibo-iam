package com.takibo.iamboot.ui;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Une ressource inexistante (ex. {@code /assets/absent.js}) répond 404. Sans ce handler, le
 * {@code SentinelAdvice} générique la transforme en 500.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class NotFoundAdvice {

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Void> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
