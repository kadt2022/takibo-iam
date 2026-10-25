package com.takibo.securitycontext.model;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueObjectsTest {

    private static final String ORG = "11111111-1111-1111-1111-111111111111";
    private static final String SPACE = "22222222-2222-2222-2222-222222222222";
    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    private static void assertRejected(String message, Executable construction) {
        assertEquals(message, assertThrows(InvalidTakiboSecurityContextException.class, construction).getMessage());
    }

    // --- SubjectIdentity ---

    @Test
    void subject_keepsValues_andCopiesRolesDefensively() {
        Set<String> roles = new HashSet<>(Set.of("ADMIN"));
        SubjectIdentity subject = new SubjectIdentity("acc-1", SubjectNature.HUMAN, roles, AuthenticationMethod.MFA);
        roles.add("INJECTED");

        assertEquals("acc-1", subject.subjectId());
        assertEquals(SubjectNature.HUMAN, subject.nature());
        assertEquals(AuthenticationMethod.MFA, subject.authenticationMethod());
        assertEquals(Set.of("ADMIN"), subject.declaredRoles());
        assertThrows(UnsupportedOperationException.class, () -> subject.declaredRoles().add("X"));
    }

    @Test
    void subject_missingRolesBecomeEmpty() {
        assertEquals(Set.of(), new SubjectIdentity("acc-1", SubjectNature.SERVICE, null, AuthenticationMethod.API_KEY).declaredRoles());
    }

    @Test
    void subject_requiresIdNatureAndMethod() {
        assertRejected("subjectId is required", () -> new SubjectIdentity(" ", SubjectNature.HUMAN, null, AuthenticationMethod.PASSWORD));
        assertRejected("nature is required", () -> new SubjectIdentity("a", null, null, AuthenticationMethod.PASSWORD));
        assertRejected("authenticationMethod is required", () -> new SubjectIdentity("a", SubjectNature.HUMAN, null, null));
    }

    @Test
    void subject_rejectsBlankOrOversizedRoles() {
        assertRejected("declaredRoles must not contain null/blank values",
                () -> new SubjectIdentity("a", SubjectNature.HUMAN, Set.of(" "), AuthenticationMethod.PASSWORD));
        assertDoesNotThrow(() -> new SubjectIdentity("a", SubjectNature.HUMAN, Set.of("R".repeat(128)), AuthenticationMethod.PASSWORD));
        assertRejected("declaredRoles contains an oversized role",
                () -> new SubjectIdentity("a", SubjectNature.HUMAN, Set.of("R".repeat(129)), AuthenticationMethod.PASSWORD));
    }

    // --- TenantScope ---

    @Test
    void tenant_scopeLevels() {
        TenantScope global = new TenantScope(null, null);
        TenantScope org = new TenantScope(ORG, null);
        TenantScope space = new TenantScope(ORG, SPACE);

        assertFalse(global.isOrgScoped());
        assertFalse(global.isSpaceScoped());
        assertTrue(org.isOrgScoped());
        assertFalse(org.isSpaceScoped());
        assertTrue(space.isOrgScoped());
        assertTrue(space.isSpaceScoped());
    }

    @Test
    void tenant_blankIdsMeanAbsent_andIdsAreTrimmed() {
        TenantScope scope = new TenantScope("  " + ORG + " ", "  ");

        assertEquals(ORG, scope.organizationId());
        assertNull(scope.spaceId());
        assertNull(new TenantScope(" ", "").organizationId());
    }

    @Test
    void tenant_rejectsInvalidUuids_andSpaceWithoutOrganization() {
        assertRejected("Invalid UUID for organizationId: acme", () -> new TenantScope("acme", null));
        assertRejected("Invalid UUID for spaceId: s-1", () -> new TenantScope(ORG, "s-1"));
        assertRejected("spaceId requires organizationId", () -> new TenantScope(null, SPACE));
    }

    // --- TransportContext ---

    @Test
    void transport_normalizesBlankValues_andAcceptsAbsentPort() {
        TransportContext transport = new TransportContext(" req-1 ", " 203.0.113.7 ", " ", "https", "", null, " api.takibo.io ", 0);

        assertEquals("req-1", transport.requestId());
        assertEquals("203.0.113.7", transport.ipAddress());
        assertNull(transport.forwardedFor());
        assertEquals("https", transport.protocol());
        assertNull(transport.tlsVersion());
        assertNull(transport.cipherSuite());
        assertEquals("api.takibo.io", transport.host());
        assertEquals(0, transport.port());
    }

    @Test
    void transport_rejectsInvalidValues() {
        assertRejected("ipAddress too long", () -> new TransportContext(null, "1".repeat(129), null, null, null, null, null, 0));
        assertRejected("requestId too long", () -> new TransportContext("r".repeat(129), null, null, null, null, null, null, 0));
        assertRejected("host too long", () -> new TransportContext(null, null, null, null, null, null, "h".repeat(256), 0));
        assertRejected("port must be between 1 and 65535", () -> new TransportContext(null, null, null, null, null, null, null, 70000));
        assertRejected("Invalid IP address for ipAddress: [::1", () -> new TransportContext(null, "[::1", null, null, null, null, null, 443));
    }

    // --- TemporalContext ---

    @Test
    void temporal_validation() {
        TemporalContext temporal = new TemporalContext(NOW, "corr-1", "https://issuer", 1);
        assertEquals(NOW, temporal.issuedAt());
        assertEquals(1, temporal.schemaVersion());

        assertRejected("issuedAt is required", () -> new TemporalContext(null, null, null, 1));
        assertRejected("schemaVersion must be >= 1", () -> new TemporalContext(NOW, null, null, 0));
        assertRejected("correlationId too long", () -> new TemporalContext(NOW, "c".repeat(129), null, 1));
        assertRejected("issuer too long", () -> new TemporalContext(NOW, null, "i".repeat(257), 1));
    }

    // --- ClientContext ---

    @Test
    void client_validation() {
        assertDoesNotThrow(() -> new ClientContext(null, null, null, null));
        assertDoesNotThrow(() -> new ClientContext("u".repeat(2048), ClientChannel.BROWSER, "p".repeat(128), "d".repeat(256)));

        assertRejected("userAgent too long", () -> new ClientContext("u".repeat(2049), ClientChannel.API, null, null));
        assertRejected("platform too long", () -> new ClientContext(null, ClientChannel.API, "p".repeat(129), null));
        assertRejected("deviceId too long", () -> new ClientContext(null, ClientChannel.API, null, "d".repeat(257)));
    }
}
