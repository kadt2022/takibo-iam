package com.takibo.securitycontext.validation;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Les adresses IP testées sont toutes littérales : aucune résolution DNS n'est déclenchée.
 */
class TakiboSecurityContextValidatorsTest {

    @Test
    void normalizeToNull_trimsAndTurnsBlankIntoNull() {
        assertNull(TakiboSecurityContextValidators.normalizeToNull(null));
        assertNull(TakiboSecurityContextValidators.normalizeToNull(""));
        assertNull(TakiboSecurityContextValidators.normalizeToNull("   "));
        assertEquals("value", TakiboSecurityContextValidators.normalizeToNull("  value \t"));
    }

    @Test
    void validateUuid_acceptsUuids_andRejectsAnythingElseWithTheCause() {
        assertDoesNotThrow(() -> TakiboSecurityContextValidators.validateUuid("11111111-1111-1111-1111-111111111111", "orgId"));

        InvalidTakiboSecurityContextException error = assertThrows(InvalidTakiboSecurityContextException.class,
                () -> TakiboSecurityContextValidators.validateUuid("not-a-uuid", "orgId"));
        assertEquals("Invalid UUID for orgId: not-a-uuid", error.getMessage());
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    @Test
    void validatePortOptional_zeroMeansAbsent_otherwiseMustBeAValidPort() {
        assertDoesNotThrow(() -> TakiboSecurityContextValidators.validatePortOptional(0, "port"));
        assertDoesNotThrow(() -> TakiboSecurityContextValidators.validatePortOptional(1, "port"));
        assertDoesNotThrow(() -> TakiboSecurityContextValidators.validatePortOptional(65535, "port"));

        for (int invalid : new int[] {-1, 65536}) {
            InvalidTakiboSecurityContextException error = assertThrows(InvalidTakiboSecurityContextException.class,
                    () -> TakiboSecurityContextValidators.validatePortOptional(invalid, "port"));
            assertEquals("port must be between 1 and 65535", error.getMessage());
        }
    }

    @Test
    void validateIpAddressOptional_acceptsIpv4AndIpv6Literals_andAbsentValue() {
        assertDoesNotThrow(() -> TakiboSecurityContextValidators.validateIpAddressOptional(null, "ip"));
        assertDoesNotThrow(() -> TakiboSecurityContextValidators.validateIpAddressOptional("203.0.113.7", "ip"));
        assertDoesNotThrow(() -> TakiboSecurityContextValidators.validateIpAddressOptional("2001:db8::1", "ip"));
        assertDoesNotThrow(() -> TakiboSecurityContextValidators.validateIpAddressOptional("::1", "ip"));
    }

    @Test
    void validateIpAddressOptional_rejectsMalformedIpv6Literal() {
        InvalidTakiboSecurityContextException error = assertThrows(InvalidTakiboSecurityContextException.class,
                () -> TakiboSecurityContextValidators.validateIpAddressOptional("[::1", "ip"));
        assertEquals("Invalid IP address for ip: [::1", error.getMessage());
        assertInstanceOf(Exception.class, error.getCause());
    }
}
