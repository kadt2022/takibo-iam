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
class TakiboSecurityCurrentSpaceContextAdapterTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Mock
    private CurrentTakiboSecurityContextProvider contextProvider;

    @InjectMocks
    private TakiboSecurityCurrentSpaceContextAdapter adapter;

    @Test
    void requireCurrentSpaceId_validSpace_returnsUuid() {
        when(contextProvider.current()).thenReturn(buildContext(new TenantScope(ORG_ID.toString(), SPACE_ID.toString())));

        UUID result = adapter.requireCurrentSpaceId();

        assertThat(result).isEqualTo(SPACE_ID);
    }

    @Test
    void requireCurrentSpaceId_contextNull_denied() {
        when(contextProvider.current()).thenReturn(null);

        assertThatThrownBy(() -> adapter.requireCurrentSpaceId())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("SPACE_CONTEXT_REQUIRED");
    }

    @Test
    void requireCurrentSpaceId_tenantNull_denied() {
        TakiboSecurityContext context = mock(TakiboSecurityContext.class);
        when(context.tenant()).thenReturn(null);
        when(contextProvider.current()).thenReturn(context);

        assertThatThrownBy(() -> adapter.requireCurrentSpaceId())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("SPACE_CONTEXT_REQUIRED");
    }

    @Test
    void requireCurrentSpaceId_spaceIdBlank_denied() {
        TenantScope tenant = mock(TenantScope.class);
        when(tenant.spaceId()).thenReturn(" ");
        TakiboSecurityContext context = mock(TakiboSecurityContext.class);
        when(context.tenant()).thenReturn(tenant);
        when(contextProvider.current()).thenReturn(context);

        assertThatThrownBy(() -> adapter.requireCurrentSpaceId())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("SPACE_CONTEXT_REQUIRED");
    }

    @Test
    void requireCurrentSpaceId_spaceIdMalformed_denied() {
        TenantScope tenant = mock(TenantScope.class);
        when(tenant.spaceId()).thenReturn("not-a-uuid");
        TakiboSecurityContext context = mock(TakiboSecurityContext.class);
        when(context.tenant()).thenReturn(tenant);
        when(contextProvider.current()).thenReturn(context);

        assertThatThrownBy(() -> adapter.requireCurrentSpaceId())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("SPACE_CONTEXT_MALFORMED");
    }

    private TakiboSecurityContext buildContext(TenantScope tenant) {
        return TakiboSecurityContext.builder()
                .subject(new SubjectIdentity("svc-test", SubjectNature.SERVICE, Set.of(), AuthenticationMethod.OAUTH2))
                .tenant(tenant)
                .temporal(new TemporalContext(Instant.now(), null, null, 1))
                .build();
    }
}
