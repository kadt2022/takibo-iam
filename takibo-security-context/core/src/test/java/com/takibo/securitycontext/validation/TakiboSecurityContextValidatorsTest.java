package com.takibo.securitycontext.validation;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }

    @Test
    void validateIpAddressOptional_rejectsHostNames() {
        InvalidTakiboSecurityContextException error = assertThrows(InvalidTakiboSecurityContextException.class,
                () -> TakiboSecurityContextValidators.validateIpAddressOptional("localhost", "ipAddress"));
        assertEquals("Invalid IP address for ipAddress: localhost", error.getMessage());
    }

    @Test
    void isIpLiteral_acceptsStrictIpv4AndIpv6Literals() {
        for (String ip : new String[] {"0.0.0.0", "203.0.113.7", "255.255.255.255", "::1", "::",
                "2001:db8::1", "2001:DB8:0:0:8:800:200C:417A", "::ffff:192.0.2.1", "[2001:db8::1]"}) {
            assertTrue(TakiboSecurityContextValidators.isIpLiteral(ip), ip);
        }
    }

    @Test
    void isIpLiteral_rejectsHostNamesAndMalformedValues() {
        for (String value : new String[] {null, "", "localhost", "unknown", "evil.example.com", "1.2.3",
                "1.2.3.4.5", "256.1.1.1", "01.2.3.4", "1.2.3.-4", "1..2.3", "1.2.3.4:8080", "[::1", "[]",
                "2001:db8::zz", ":::1:", "::1%eth0", "１.２.３.４", "1.2.3.４", "fe80::1 ", " 1.2.3.4"}) {
            assertFalse(TakiboSecurityContextValidators.isIpLiteral(value), String.valueOf(value));
        }
    }

    @Test
    void ipValidation_neverTriggersADnsLookup() {
        // Noms uniques à chaque exécution : le cache DNS du JDK ne peut pas masquer une résolution.
        String run = java.util.UUID.randomUUID().toString().substring(0, 8);

        // Contrôle : le résolveur espion voit bien une vraie résolution.
        RecordingResolverProvider.LOOKUPS.clear();
        String control = "control-" + run + ".invalid";
        assertThrows(java.net.UnknownHostException.class, () -> java.net.InetAddress.getByName(control));
        assertEquals(java.util.List.of(control), RecordingResolverProvider.LOOKUPS);

        RecordingResolverProvider.LOOKUPS.clear();
        for (String value : new String[] {"evil-" + run + ".example.com", "attacker-" + run + ".invalid",
                "unknown-" + run, "999.1.1.1", "1.2.3", "2001:db8::1", "::1", "203.0.113.7", "deadbeef", "cafe::zz"}) {
            TakiboSecurityContextValidators.isIpLiteral(value);
            try {
                TakiboSecurityContextValidators.validateIpAddressOptional(value, "ipAddress");
            } catch (InvalidTakiboSecurityContextException expected) {
                // valeur refusée : seul compte ici l'absence de résolution DNS
            }
        }
        assertEquals(java.util.List.of(), RecordingResolverProvider.LOOKUPS);
    }
}
