package com.takibo.securitycontext.validation;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TakiboAssertsTest {

    private static void assertRejected(String message, Executable call) {
        assertEquals(message, assertThrows(InvalidTakiboSecurityContextException.class, call).getMessage());
    }

    @Test
    void notNull() {
        assertDoesNotThrow(() -> TakiboAsserts.notNull("x", "required"));
        assertRejected("required", () -> TakiboAsserts.notNull(null, "required"));
    }

    @Test
    void notBlank() {
        assertDoesNotThrow(() -> TakiboAsserts.notBlank("x", "required"));
        assertRejected("required", () -> TakiboAsserts.notBlank(null, "required"));
        assertRejected("required", () -> TakiboAsserts.notBlank(" \t", "required"));
    }

    @Test
    void isTrue() {
        assertDoesNotThrow(() -> TakiboAsserts.isTrue(true, "broken"));
        assertRejected("broken", () -> TakiboAsserts.isTrue(false, "broken"));
    }

    @Test
    void min_isInclusive() {
        assertDoesNotThrow(() -> TakiboAsserts.min(1, 1, "schemaVersion"));
        assertRejected("schemaVersion must be >= 1", () -> TakiboAsserts.min(0, 1, "schemaVersion"));
    }

    @Test
    void maxLength_isInclusive_andIgnoresNull() {
        assertDoesNotThrow(() -> TakiboAsserts.maxLength(null, 3, "too long"));
        assertDoesNotThrow(() -> TakiboAsserts.maxLength("abc", 3, "too long"));
        assertRejected("too long", () -> TakiboAsserts.maxLength("abcd", 3, "too long"));
    }
}
