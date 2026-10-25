package com.takibo.securitycontext.model;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TakiboSecurityContextTest {

    private static final SubjectIdentity SUBJECT = new SubjectIdentity("acc-1", SubjectNature.HUMAN, Set.of("USER"), AuthenticationMethod.OIDC);
    private static final TenantScope TENANT = new TenantScope("11111111-1111-1111-1111-111111111111", null);
    private static final TemporalContext TEMPORAL = new TemporalContext(Instant.parse("2026-10-25T10:00:00Z"), "corr", "iss", 1);

    @Test
    void build_keepsEveryPart() {
        TransportContext transport = new TransportContext("req", "203.0.113.7", null, "https", null, null, "host", 443);
        ClientContext client = new ClientContext("agent", ClientChannel.API, null, null);
        ContextAttributeStore attributes = new ContextAttributeStore(Map.of(StandardAttributeKeys.ACCOUNT_ID, "acc-1"));

        TakiboSecurityContext context = TakiboSecurityContext.builder()
                .subject(SUBJECT).tenant(TENANT).temporal(TEMPORAL)
                .transport(transport).client(client).attributes(attributes)
                .build();

        assertSame(SUBJECT, context.subject());
        assertSame(TENANT, context.tenant());
        assertSame(TEMPORAL, context.temporal());
        assertSame(transport, context.transport());
        assertSame(client, context.client());
        assertSame(attributes, context.attributes());
    }

    @Test
    void transportClientAndAttributesAreOptional() {
        TakiboSecurityContext context = TakiboSecurityContext.builder().subject(SUBJECT).tenant(TENANT).temporal(TEMPORAL).build();

        assertNull(context.transport());
        assertNull(context.client());
        assertTrue(context.attributes().asMap().isEmpty());
    }

    @Test
    void subjectTenantAndTemporalAreRequired() {
        assertEquals("subject required", assertThrows(InvalidTakiboSecurityContextException.class,
                () -> TakiboSecurityContext.builder().tenant(TENANT).temporal(TEMPORAL).build()).getMessage());
        assertEquals("tenant required", assertThrows(InvalidTakiboSecurityContextException.class,
                () -> TakiboSecurityContext.builder().subject(SUBJECT).temporal(TEMPORAL).build()).getMessage());
        assertEquals("temporal required", assertThrows(InvalidTakiboSecurityContextException.class,
                () -> TakiboSecurityContext.builder().subject(SUBJECT).tenant(TENANT).build()).getMessage());
    }
}
