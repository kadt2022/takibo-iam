package com.takibo.messaging.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipientTest {

    @Test
    void exposesTypeValueAndKey() {
        Recipient recipient = new Recipient("EMAIL", "Alice@Takibo.test", "email:alice@takibo.test");

        assertEquals("EMAIL", recipient.type());
        assertEquals("Alice@Takibo.test", recipient.value());
        assertEquals("email:alice@takibo.test", recipient.key());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsMissingType(String type) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Recipient(type, "a@b.c", "email:a@b.c"));
        assertEquals("type is required", ex.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsMissingValue(String value) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Recipient("EMAIL", value, "email:a@b.c"));
        assertEquals("value is required", ex.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsMissingKey(String key) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Recipient("EMAIL", "a@b.c", key));
        assertEquals("key is required", ex.getMessage());
    }

    @Test
    void recipientsWithSameFieldsAreEqual() {
        Recipient a = new Recipient("EMAIL", "a@b.c", "email:a@b.c");
        Recipient b = new Recipient("EMAIL", "a@b.c", "email:a@b.c");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a);
    }

    @Test
    void recipientsDifferingByAnyFieldAreNotEqual() {
        Recipient reference = new Recipient("EMAIL", "a@b.c", "email:a@b.c");

        assertNotEquals(reference, new Recipient("SMS", "a@b.c", "email:a@b.c"));
        assertNotEquals(reference, new Recipient("EMAIL", "A@b.c", "email:a@b.c"));
        assertNotEquals(reference, new Recipient("EMAIL", "a@b.c", "email:other"));
        assertFalse(reference.equals("a@b.c"));
        assertFalse(reference.equals(null));
    }

    @Test
    void toStringDescribesRecipient() {
        Recipient recipient = new Recipient("EMAIL", "a@b.c", "email:a@b.c");

        assertEquals("Recipient{type='EMAIL', key='email:a@b.c', value='a@b.c'}", recipient.toString());
    }
}
