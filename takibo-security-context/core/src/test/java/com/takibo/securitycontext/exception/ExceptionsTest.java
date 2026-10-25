package com.takibo.securitycontext.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExceptionsTest {

    @Test
    void allContextErrorsShareOneUncheckedBaseType_andKeepTheirCause() {
        IllegalStateException cause = new IllegalStateException("root");

        TakiboSecurityContextException base = new TakiboSecurityContextException("base", cause);
        InvalidTakiboSecurityContextException invalid = new InvalidTakiboSecurityContextException("invalid", cause);
        TakiboSecurityContextNotAvailableException missing = new TakiboSecurityContextNotAvailableException("missing");

        assertInstanceOf(RuntimeException.class, base);
        assertInstanceOf(TakiboSecurityContextException.class, invalid);
        assertInstanceOf(TakiboSecurityContextException.class, missing);
        assertSame(cause, base.getCause());
        assertSame(cause, invalid.getCause());
        assertEquals("missing", missing.getMessage());
        assertEquals("base", new TakiboSecurityContextException("base").getMessage());
        assertEquals("invalid", new InvalidTakiboSecurityContextException("invalid").getMessage());
    }
}
