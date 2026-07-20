package com.takibo.managementservice.infrastructure.jpa.support;

import java.util.Locale;

public final class DatabaseConstraintViolation {

    private DatabaseConstraintViolation() {
    }

    public static boolean mentions(Throwable failure, String... constraintNames) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                for (String constraintName : constraintNames) {
                    if (normalized.contains(constraintName.toLowerCase(Locale.ROOT))) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
