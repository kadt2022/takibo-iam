package com.takibo.managementservice.application.service;

import com.takibo.identitycore.application.identity.command.ProvisionFounderUserCommand;
import com.takibo.identitycore.application.identity.port.AccountApplicationCase;
import com.takibo.identitycore.application.identity.port.FounderUserProvisioningCase;
import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
import com.takibo.identitycore.interfaces.rest.response.AccountResponse;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import com.takibo.managementservice.application.command.CreateOrganizationCommand;
import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.provisioning.TechnicalRbacProvision;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.interfaces.rest.request.AccountInput;
import com.takibo.managementservice.interfaces.rest.request.OrganizationInput;
import com.takibo.managementservice.interfaces.rest.request.OrganizationSignupRequest;
import com.takibo.managementservice.interfaces.rest.request.ProfileInput;
import com.takibo.managementservice.interfaces.rest.request.SpaceInput;
import com.takibo.managementservice.interfaces.rest.response.SpaceResponse;
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
    @Mock private AccountApplicationCase accountApp;
    @Mock private FounderUserProvisioningCase founderProvisioning;
    @Mock private CurrentOrganizationContextCase currentOrganizationContext;
    @Mock private TechnicalRbacProvision technicalRbacProvision;

    @InjectMocks private OrganizationSignupService service;

    @Test
    void given_missing_organization_id_when_signup_then_creates_organization_account_space_founder_and_rbac() {
        OrganizationSignupRequest req = request(new OrganizationInput(null, "Takibo IAM", "Takibo"));
        when(orgApp.create("Takibo IAM", "Takibo"))
                .thenReturn(new CreateOrganizationCommand(ORG_ID, "takibo-iam", "Takibo"));
        when(accountApp.createAccountInOrg(ORG_ID, "founder@takibo.io", "Str0ng!Passw0rd"))
                .thenReturn(new AccountResponse(ACCOUNT_ID, "founder@takibo.io"));
        when(spaceApp.createSpace(any(CreateSpaceCommand.class))).thenReturn(spaceResponse());
        when(founderProvisioning.provisionFounder(any(ProvisionFounderUserCommand.class))).thenReturn(userResponse());

        var response = service.signup(req);

        assertThat(response.organizationId()).isEqualTo(ORG_ID);
        assertThat(response.spaceId()).isEqualTo(SPACE_ID);
        assertThat(response.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);

        ArgumentCaptor<CreateSpaceCommand> spaceCaptor = ArgumentCaptor.forClass(CreateSpaceCommand.class);
        verify(spaceApp).createSpace(spaceCaptor.capture());
        assertThat(spaceCaptor.getValue().orgId()).isEqualTo(ORG_ID);
        assertThat(spaceCaptor.getValue().ownerAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(spaceCaptor.getValue().code()).isEqualTo("Finance Space");

        ArgumentCaptor<ProvisionFounderUserCommand> founderCaptor =
                ArgumentCaptor.forClass(ProvisionFounderUserCommand.class);
        verify(founderProvisioning).provisionFounder(founderCaptor.capture());
        assertThat(founderCaptor.getValue().organizationId()).isEqualTo(ORG_ID);
        assertThat(founderCaptor.getValue().spaceId()).isEqualTo(SPACE_ID);
        assertThat(founderCaptor.getValue().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(founderCaptor.getValue().username()).isEqualTo("founder");

        verify(technicalRbacProvision).provisionFounder(ORG_ID, SPACE_ID, ACCOUNT_ID, "SYSTEM");
        verify(currentOrganizationContext, never()).requireCurrentOrganizationId();
    }

    @Test
    void given_existing_organization_matching_current_context_when_signup_then_continues_without_creating_organization() {
        OrganizationSignupRequest req = request(new OrganizationInput(ORG_ID, "Takibo IAM", "Takibo"));
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_ID);
        when(accountApp.createAccountInOrg(ORG_ID, "founder@takibo.io", "Str0ng!Passw0rd"))
                .thenReturn(new AccountResponse(ACCOUNT_ID, "founder@takibo.io"));
        when(spaceApp.createSpace(any(CreateSpaceCommand.class))).thenReturn(spaceResponse());
        when(founderProvisioning.provisionFounder(any(ProvisionFounderUserCommand.class))).thenReturn(userResponse());

        var response = service.signup(req);

        assertThat(response.organizationId()).isEqualTo(ORG_ID);
        verify(orgApp, never()).create(any(), any());
        verify(founderProvisioning).provisionFounder(any(ProvisionFounderUserCommand.class));
        verify(technicalRbacProvision).provisionFounder(ORG_ID, SPACE_ID, ACCOUNT_ID, "SYSTEM");
    }

    @Test
    void given_existing_organization_not_matching_current_context_when_signup_then_throws_access_denied() {
        UUID otherOrg = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
        OrganizationSignupRequest req = request(new OrganizationInput(ORG_ID, "Takibo IAM", "Takibo"));
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(otherOrg);

        assertThatThrownBy(() -> service.signup(req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ORG_OWNERSHIP_REQUIRED");

        verify(accountApp, never()).createAccountInOrg(any(), any(), any());
        verify(spaceApp, never()).createSpace(any());
        verify(founderProvisioning, never()).provisionFounder(any());
        verify(technicalRbacProvision, never()).provisionFounder(any(), any(), any(), any());
    }

    private OrganizationSignupRequest request(OrganizationInput organization) {
        return new OrganizationSignupRequest(
                organization,
                new SpaceInput("Finance Space", "Finance", "Finance workspace"),
                new AccountInput("founder@takibo.io", "Str0ng!Passw0rd"),
                new ProfileInput("founder", "Tresor", "Kadima")
        );
    }

    private SpaceResponse spaceResponse() {
        return new SpaceResponse(
                SPACE_ID, ORG_ID, "finance", "Finance", "Finance workspace",
                SpaceStatus.ACTIVE, null, null, ACCOUNT_ID, null, null, 0L
        );
    }

    private UserResponse userResponse() {
        return new UserResponse(
                USER_ID, SPACE_ID, "founder", "founder@takibo.io",
                "Tresor", "Kadima", null, null,
                false, false, null, null, null, 0L
        );
    }
}
