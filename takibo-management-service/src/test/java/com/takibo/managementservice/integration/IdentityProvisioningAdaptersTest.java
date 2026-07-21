package com.takibo.managementservice.integration;

import com.takibo.identitycore.application.identity.command.ProvisionFounderUserCommand;
import com.takibo.identitycore.application.identity.port.AccountApplicationCase;
import com.takibo.identitycore.application.identity.port.FounderUserProvisioningCase;
import com.takibo.identitycore.interfaces.rest.response.AccountResponse;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityProvisioningAdaptersTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Test
    void account_adapter_returns_only_the_identity_identifier_to_the_application() {
        AccountApplicationCase accounts = mock(AccountApplicationCase.class);
        when(accounts.createAccountInOrg(ORGANIZATION_ID, "founder@takibo.io", "secret"))
                .thenReturn(new AccountResponse(ACCOUNT_ID, "founder@takibo.io"));

        UUID result = new IdentityAccountProvisioningAdapter(accounts)
                .createAccount(ORGANIZATION_ID, "founder@takibo.io", "secret");

        assertThat(result).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void founder_adapter_maps_the_local_port_to_the_identity_command() {
        FounderUserProvisioningCase founders = mock(FounderUserProvisioningCase.class);
        when(founders.provisionFounder(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UserResponse(
                        USER_ID, SPACE_ID, "founder", "founder@takibo.io",
                        "Tresor", "Kadima", null, null,
                        false, false, null, null, null, 0L));

        UUID result = new IdentityFounderProvisioningAdapter(founders).provisionFounder(
                ORGANIZATION_ID, SPACE_ID, ACCOUNT_ID, "founder", "Tresor", "Kadima");

        ArgumentCaptor<ProvisionFounderUserCommand> command =
                ArgumentCaptor.forClass(ProvisionFounderUserCommand.class);
        verify(founders).provisionFounder(command.capture());
        assertThat(command.getValue().organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(command.getValue().spaceId()).isEqualTo(SPACE_ID);
        assertThat(command.getValue().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(command.getValue().username()).isEqualTo("founder");
        assertThat(result).isEqualTo(USER_ID);
    }
}
