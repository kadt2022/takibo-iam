package com.takibo.managementservice.integration;

import com.takibo.identitycore.application.rbac.governance.port.in.GroupAssignmentCase;
import com.takibo.identitycore.application.rbac.governance.port.in.RoleAssignmentCase;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdentityTechnicalRbacProvisioningAdapterTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final String SYSTEM_ACTOR = "system";

    @Mock private RoleAssignmentCase roleAssignmentCase;
    @Mock private GroupAssignmentCase groupAssignmentCase;

    @InjectMocks private IdentityTechnicalRbacProvisioningAdapter provision;

    @Test
    void provisionFounder_assignsOrgAuthorityAtOrgLevelAndSpaceAuthorityAtSpaceLevel() {
        provision.provisionFounder(ORG_ID, SPACE_ID, ACCOUNT_ID, SYSTEM_ACTOR);

        ArgumentCaptor<Identity> identityCaptor = ArgumentCaptor.forClass(Identity.class);
        verify(roleAssignmentCase)
                .assignTechnicalRole(eq(ORG_ID), isNull(), identityCaptor.capture(), eq("R_ORG_OWNER"), eq(SYSTEM_ACTOR));
        verify(groupAssignmentCase)
                .assignTechnicalGroup(eq(ORG_ID), isNull(), identityCaptor.capture(), eq("G_ORG_ADMINS"), eq(SYSTEM_ACTOR));
        verify(roleAssignmentCase)
                .assignTechnicalRole(eq(ORG_ID), eq(SPACE_ID), identityCaptor.capture(), eq("R_SPACE_ADMIN"), eq(SYSTEM_ACTOR));
        verify(groupAssignmentCase)
                .assignTechnicalGroup(eq(ORG_ID), eq(SPACE_ID), identityCaptor.capture(), eq("G_SPACE_ADMINS"), eq(SYSTEM_ACTOR));

        assertThat(identityCaptor.getAllValues())
                .allSatisfy(identity -> {
                    assertThat(identity.type()).isEqualTo(IdentityType.ACCOUNT);
                    assertThat(identity.id()).isEqualTo(ACCOUNT_ID);
                });
    }

    @Test
    void provisionSpaceCreator_assignsOnlySpaceAdminAuthority() {
        provision.provisionSpaceCreator(ORG_ID, SPACE_ID, ACCOUNT_ID, SYSTEM_ACTOR);

        ArgumentCaptor<Identity> identityCaptor = ArgumentCaptor.forClass(Identity.class);
        verify(roleAssignmentCase)
                .assignTechnicalRole(eq(ORG_ID), eq(SPACE_ID), identityCaptor.capture(), eq("R_SPACE_ADMIN"), eq(SYSTEM_ACTOR));
        verify(groupAssignmentCase)
                .assignTechnicalGroup(eq(ORG_ID), eq(SPACE_ID), identityCaptor.capture(), eq("G_SPACE_ADMINS"), eq(SYSTEM_ACTOR));

        assertThat(identityCaptor.getAllValues()).allMatch(identity -> ACCOUNT_ID.equals(identity.id()));
    }
}
