package com.takibo.securitycontext.validation;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;

public final class TakiboAsserts {

    private TakiboAsserts() {
    }

    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new InvalidTakiboSecurityContextException(message);
        }
    }

    public static void notBlank(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new InvalidTakiboSecurityContextException(message);
        }
    }

    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new InvalidTakiboSecurityContextException(message);
        }
    }

    public static void min(int value, int min, String fieldName) {
        if (value < min) {
            throw new InvalidTakiboSecurityContextException(fieldName + " must be >= " + min);
        }
    }

    public static void maxLength(String text, int max, String message) {
        if (text != null && text.length() > max) {
            throw new InvalidTakiboSecurityContextException(message);
        }
    }
}
