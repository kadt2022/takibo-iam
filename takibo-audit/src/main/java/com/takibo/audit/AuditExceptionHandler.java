package com.takibo.audit;

import com.takibo.audit.api.AuditStoreException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class AuditExceptionHandler {

    @ExceptionHandler(AuditStoreException.class)
    public ResponseEntity<String> handleStoreException(AuditStoreException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Audit storage failed: " + ex.getMessage());
    }
}
