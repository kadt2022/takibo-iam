package com.takibo.iamboot.security;

import com.takibo.securitycontext.model.AuthenticationMethod;
import com.takibo.securitycontext.model.SubjectIdentity;
import com.takibo.securitycontext.model.SubjectNature;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.model.TenantScope;
import com.takibo.securitycontext.model.TemporalContext;
import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TakiboSecurityCurrentOrganizationContextAdapterTest {

    private static final UUID VALID_ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Mock
    private CurrentTakiboSecurityContextProvider contextProvider;

    @InjectMocks
    private TakiboSecurityCurrentOrganizationContextAdapter adapter;

    @Test
    void requireCurrentOrganizationId_providerReturnsNull_denied() {
        when(contextProvider.current()).thenReturn(null);

        assertThatThrownBy(() -> adapter.requireCurrentOrganizationId())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ORG_CONTEXT_REQUIRED");
    }

    @Test
    void requireCurrentOrganizationId_tenantNull_denied() {
        TakiboSecurityContext context = mock(TakiboSecurityContext.class);
        when(context.tenant()).thenReturn(null);
        when(contextProvider.current()).thenReturn(context);

        assertThatThrownBy(() -> adapter.requireCurrentOrganizationId())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ORG_CONTEXT_REQUIRED");
    }

    @Test
    void requireCurrentOrganizationId_organizationIdNull_denied() {
        when(contextProvider.current()).thenReturn(buildContext(new TenantScope(null, null)));

        assertThatThrownBy(() -> adapter.requireCurrentOrganizationId())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ORG_CONTEXT_REQUIRED");
    }

    @Test
    void requireCurrentOrganizationId_organizationIdMalformed_malformed() {
        // TenantScope validates UUID format in its compact constructor so we mock to bypass it
        TenantScope tenant = mock(TenantScope.class);
        when(tenant.organizationId()).thenReturn("not-a-uuid");
        TakiboSecurityContext context = mock(TakiboSecurityContext.class);
        when(context.tenant()).thenReturn(tenant);
        when(contextProvider.current()).thenReturn(context);

        assertThatThrownBy(() -> adapter.requireCurrentOrganizationId())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ORG_CONTEXT_MALFORMED");
    }

    @Test
    void requireCurrentOrganizationId_valid_returnsUuid() {
        when(contextProvider.current()).thenReturn(buildContext(new TenantScope(VALID_ORG_ID.toString(), null)));

        UUID result = adapter.requireCurrentOrganizationId();

        assertThat(result).isEqualTo(VALID_ORG_ID);
    }

    private TakiboSecurityContext buildContext(TenantScope tenant) {
        return TakiboSecurityContext.builder()
                .subject(new SubjectIdentity("svc-test", SubjectNature.SERVICE, Set.of(), AuthenticationMethod.OAUTH2))
                .tenant(tenant)
                .temporal(new TemporalContext(Instant.now(), null, null, 1))
                .build();
    }
}
