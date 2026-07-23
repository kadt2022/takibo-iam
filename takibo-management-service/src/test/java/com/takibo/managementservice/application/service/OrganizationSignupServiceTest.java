package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.result.CreateSpaceResult;
import com.takibo.managementservice.application.command.OrganizationSignupCommand;
import com.takibo.managementservice.application.port.FounderProvisioningPort;
import com.takibo.managementservice.application.port.OrganizationAccountProvisioningPort;
import com.takibo.managementservice.application.port.TechnicalRbacProvisioningPort;
import com.takibo.managementservice.application.result.OrganizationResult;
import com.takibo.managementservice.domain.model.SpaceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationSignupServiceTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock private OrganizationApplicationService orgApp;
    @Mock private SpaceApplicationService spaceApp;
    @Mock private OrganizationAccountProvisioningPort accountProvisioning;
    @Mock private FounderProvisioningPort founderProvisioning;
    @Mock private TechnicalRbacProvisioningPort technicalRbacProvisioning;

    @InjectMocks private OrganizationSignupService service;

    @Test
    void given_missing_organization_id_when_signup_then_creates_organization_account_space_founder_and_rbac() {
        OrganizationSignupCommand command = command(null);
        when(orgApp.create("Takibo IAM", "Takibo"))
                .thenReturn(new OrganizationResult(ORG_ID, "takibo-iam", "Takibo"));
        when(accountProvisioning.createAccount(ORG_ID, "founder@takibo.io", "Str0ng!Passw0rd"))
                .thenReturn(ACCOUNT_ID);
        when(spaceApp.createSpace(any(CreateSpaceCommand.class))).thenReturn(spaceResult());
        when(founderProvisioning.provisionFounder(
                ORG_ID, SPACE_ID, ACCOUNT_ID, "founder", "Tresor", "Kadima"))
                .thenReturn(USER_ID);

        var response = service.signup(command);

        assertThat(response.organizationId()).isEqualTo(ORG_ID);
        assertThat(response.spaceId()).isEqualTo(SPACE_ID);
        assertThat(response.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);

        ArgumentCaptor<CreateSpaceCommand> spaceCaptor = ArgumentCaptor.forClass(CreateSpaceCommand.class);
        verify(spaceApp).createSpace(spaceCaptor.capture());
        assertThat(spaceCaptor.getValue().orgId()).isEqualTo(ORG_ID);
        assertThat(spaceCaptor.getValue().ownerAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(spaceCaptor.getValue().code()).isEqualTo("Finance Space");

        verify(founderProvisioning).provisionFounder(
                ORG_ID, SPACE_ID, ACCOUNT_ID, "founder", "Tresor", "Kadima");

        verify(technicalRbacProvisioning).provisionFounder(ORG_ID, SPACE_ID, ACCOUNT_ID, "SYSTEM");
    }

    @Test
    void given_existing_organization_when_signup_then_denies_before_any_side_effect() {
        OrganizationSignupCommand command = command(ORG_ID);

        assertThatThrownBy(() -> service.signup(command))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("EXISTING_ORGANIZATION_SIGNUP_FORBIDDEN");

        verify(orgApp, never()).create(any(), any());
        verify(accountProvisioning, never()).createAccount(any(), any(), any());
        verify(spaceApp, never()).createSpace(any());
        verify(founderProvisioning, never()).provisionFounder(any(), any(), any(), any(), any(), any());
        verify(technicalRbacProvisioning, never()).provisionFounder(any(), any(), any(), any());
    }

    private OrganizationSignupCommand command(UUID organizationId) {
        return new OrganizationSignupCommand(
                new OrganizationSignupCommand.Organization(organizationId, "Takibo IAM", "Takibo"),
                new OrganizationSignupCommand.Space("Finance Space", "Finance", "Finance workspace"),
                new OrganizationSignupCommand.Account("founder@takibo.io", "Str0ng!Passw0rd"),
                new OrganizationSignupCommand.Profile("founder", "Tresor", "Kadima")
        );
    }

    private CreateSpaceResult spaceResult() {
        return new CreateSpaceResult(
                SPACE_ID, ORG_ID, "finance", "Finance", "Finance workspace",
                SpaceStatus.ACTIVE, null, null, ACCOUNT_ID, null, null, 0L
        );
    }
}
