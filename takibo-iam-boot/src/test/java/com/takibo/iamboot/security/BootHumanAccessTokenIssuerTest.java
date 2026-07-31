package com.takibo.iamboot.security;

import com.takibo.authorizationserver.infrastructure.springauthserver.token.HumanTokenCommand;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.HumanTokenSigner;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.SignedHumanToken;
import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.model.HumanTokenSource;
import com.takibo.identitycore.application.auth.model.LoginToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootHumanAccessTokenIssuerTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock
    private HumanTokenSigner humanTokenSigner;

    @InjectMocks
    private BootHumanAccessTokenIssuer issuer;

    @Test
    void issue_delegatesToHumanTokenSignerAndMapsSignedToken() {
        HumanTokenRequest request = new HumanTokenRequest(
                ORG_ID,
                SPACE_ID,
                ACCOUNT_ID,
                USER_ID,
                HumanTokenSource.SPACE_SELECTION,
                List.of("R_ORG_OWNER", "R_SPACE_ADMIN"),
                List.of("G_SPACE_ADMINS"),
                List.of("P_SPACE_USERS_MANAGE")
        );
        when(humanTokenSigner.sign(org.mockito.ArgumentMatchers.any(HumanTokenCommand.class)))
                .thenReturn(new SignedHumanToken("signed.jwt", 300));

        LoginToken result = issuer.issue(request);

        ArgumentCaptor<HumanTokenCommand> captor = ArgumentCaptor.forClass(HumanTokenCommand.class);
        verify(humanTokenSigner).sign(captor.capture());
        HumanTokenCommand command = captor.getValue();
        assertThat(command.orgId()).isEqualTo(ORG_ID);
        assertThat(command.spaceId()).isEqualTo(SPACE_ID);
        assertThat(command.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.tenantSource()).isEqualTo("human_space_selection");
        assertThat(command.roles()).containsExactly("R_ORG_OWNER", "R_SPACE_ADMIN");
        assertThat(command.groups()).containsExactly("G_SPACE_ADMINS");
        assertThat(command.permissions()).containsExactly("P_SPACE_USERS_MANAGE");

        assertThat(result.accessToken()).isEqualTo("signed.jwt");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(300);
    }
}
