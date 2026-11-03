package com.takibo.identitycore.application.auth.mapper;

import com.takibo.identitycore.application.auth.command.LoginCommand;
import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.model.LoginToken;
import com.takibo.identitycore.interfaces.rest.request.LoginRequest;
import com.takibo.identitycore.interfaces.rest.response.LoginResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthMapperTest {

    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_UUID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final LoginToken TOKEN = new LoginToken("signed.jwt", "Bearer", 900L);

    private final AuthMapper mapper = Mappers.getMapper(AuthMapper.class);

    @Test
    void toCommand_carriesCredentialsAndRequestedBoundaryVerbatim() {
        LoginCommand command = mapper.toCommand(new LoginRequest("a@b.io", "s3cret!", "takibo", "finance"));

        assertThat(command).isEqualTo(new LoginCommand("a@b.io", "s3cret!", "takibo", "finance"));
        assertThat(mapper.toCommand(null)).isNull();
    }

    @Test
    void spaceScopedLogin_exposesSpaceAndLocalUser() {
        HumanTokenRequest identity = HumanTokenRequest.spaceScoped(
                ORG_UUID, SPACE_UUID, ACCOUNT_UUID, USER_UUID, List.of("R_SPACE_ADMIN"), List.of(), List.of());

        LoginResponse response = mapper.toLoginResponse(TOKEN, identity);

        assertThat(response).isEqualTo(new LoginResponse("signed.jwt", "Bearer", 900L, "SPACE",
                ORG_UUID, SPACE_UUID, ACCOUNT_UUID, USER_UUID));
    }

    @Test
    void organizationScopedLogin_hasNoSpaceNorLocalUser() {
        HumanTokenRequest identity = HumanTokenRequest.organizationScoped(
                ORG_UUID, ACCOUNT_UUID, List.of("R_ORG_OWNER"), List.of(), List.of());

        LoginResponse response = mapper.toLoginResponse(TOKEN, identity);

        assertThat(response.scopeLevel()).isEqualTo("ORGANIZATION");
        assertThat(response.organizationId()).isEqualTo(ORG_UUID);
        assertThat(response.accountId()).isEqualTo(ACCOUNT_UUID);
        assertThat(response.spaceId()).isNull();
        assertThat(response.userId()).isNull();
        assertThat(response.accessToken()).isEqualTo("signed.jwt");
    }

    @Test
    void missingToken_stillDescribesTheVerifiedIdentity() {
        HumanTokenRequest identity = HumanTokenRequest.organizationScoped(
                ORG_UUID, ACCOUNT_UUID, List.of(), List.of(), List.of());

        LoginResponse response = mapper.toLoginResponse(null, identity);

        assertThat(response.accessToken()).isNull();
        assertThat(response.expiresIn()).isZero();
        assertThat(response.organizationId()).isEqualTo(ORG_UUID);
    }

    @Test
    void bothSourcesNull_yieldsNull() {
        assertThat(mapper.toLoginResponse(null, null)).isNull();
    }
}
