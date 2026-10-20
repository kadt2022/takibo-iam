package com.takibo.securitymanagement.infrastructure.security;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;
import com.takibo.securitycontext.model.AuthenticationMethod;
import com.takibo.securitycontext.model.StandardAttributeKeys;
import com.takibo.securitycontext.model.SubjectNature;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TakiboSecurityContextFactoryTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID OTHER_ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000099");
    private static final UUID OTHER_SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000099");
    private static final UUID OTHER_USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000099");

    @Test
    void humanPasswordToken_buildsHumanPasswordContext() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "HUMAN",
                "authMethod", "PASSWORD",
                "sub", ACCOUNT_ID.toString(),
                "accountId", ACCOUNT_ID.toString(),
                "userId", USER_ID.toString(),
                "orgId", ORG_ID.toString(),
                "spaceId", SPACE_ID.toString()
        ), request());

        assertThat(context.subject().nature()).isEqualTo(SubjectNature.HUMAN);
        assertThat(context.subject().authenticationMethod()).isEqualTo(AuthenticationMethod.PASSWORD);
        assertThat(context.subject().subjectId()).isEqualTo(ACCOUNT_ID.toString());
    }

    @Test
    void absentSubjectTypeWithAccountId_fallsBackToHuman() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(Map.of(
                "accountId", ACCOUNT_ID.toString(),
                "orgId", ORG_ID.toString()
        ), request());

        assertThat(context.subject().nature()).isEqualTo(SubjectNature.HUMAN);
        assertThat(context.subject().subjectId()).isEqualTo(ACCOUNT_ID.toString());
    }

    @Test
    void clientAppWithSubject_buildsServiceOauthContext() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "CLIENT_APP",
                "sub", "postman-client"
        ), request());

        assertThat(context.subject().nature()).isEqualTo(SubjectNature.SERVICE);
        assertThat(context.subject().authenticationMethod()).isEqualTo(AuthenticationMethod.OAUTH2);
        assertThat(context.subject().subjectId()).isEqualTo("postman-client");
    }

    @Test
    void humanWithoutSubjectIdentifier_throwsInvalidContext() {
        assertThatThrownBy(() -> TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "HUMAN"
        ), request()))
                .isInstanceOf(InvalidTakiboSecurityContextException.class)
                .hasMessage("Human token must identify its subject");
    }

    @Test
    void humanAuthMethodOtherThanPassword_fallsBackToOidc() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "HUMAN",
                "authMethod", "MFA",
                "userId", USER_ID.toString()
        ), request());

        assertThat(context.subject().authenticationMethod()).isEqualTo(AuthenticationMethod.OIDC);
    }

    @Test
    void rolesClaim_populatesDeclaredRoles() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "HUMAN",
                "userId", USER_ID.toString(),
                "roles", List.of("R_ORG_OWNER", "R_SPACE_ADMIN")
        ), request());

        assertThat(context.subject().declaredRoles()).containsExactlyInAnyOrder("R_ORG_OWNER", "R_SPACE_ADMIN");
    }

    @Test
    void orgAndSpaceClaims_populateTenantScope() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "HUMAN",
                "userId", USER_ID.toString(),
                "orgId", ORG_ID.toString(),
                "spaceId", SPACE_ID.toString()
        ), request());

        assertThat(context.tenant().organizationId()).isEqualTo(ORG_ID.toString());
        assertThat(context.tenant().spaceId()).isEqualTo(SPACE_ID.toString());
    }

    @Test
    void accountAndUserClaims_populateAttributes() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "HUMAN",
                "accountId", ACCOUNT_ID.toString(),
                "userId", USER_ID.toString(),
                "orgId", ORG_ID.toString(),
                "spaceId", SPACE_ID.toString()
        ), request());

        assertThat(context.attributes().get(StandardAttributeKeys.ACCOUNT_ID, UUID.class)).contains(ACCOUNT_ID);
        assertThat(context.attributes().get(StandardAttributeKeys.USER_ID, UUID.class)).contains(USER_ID);
    }

    @Test
    void scopeLevelClaim_populatesAttributes() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "HUMAN",
                "accountId", ACCOUNT_ID.toString(),
                "orgId", ORG_ID.toString(),
                "scopeLevel", "ORGANIZATION"
        ), request());

        assertThat(context.attributes().get(StandardAttributeKeys.SCOPE_LEVEL, String.class))
                .contains("ORGANIZATION");
    }

    // --- IAM-JWT-01 : loi d'identité (J1-J6) ---

    @Test
    void j1_spaceToken_subjectIsAccount_userStaysInAttribute() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(spaceClaims(USER_ID, SPACE_ID), request());

        assertThat(context.subject().subjectId()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(context.attributes().get(StandardAttributeKeys.ACCOUNT_ID, UUID.class)).contains(ACCOUNT_ID);
        assertThat(context.attributes().get(StandardAttributeKeys.USER_ID, UUID.class)).contains(USER_ID);
    }

    @Test
    void j2_organizationToken_subjectIsAccount_noUserIsInvented() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "HUMAN",
                "sub", ACCOUNT_ID.toString(),
                "accountId", ACCOUNT_ID.toString(),
                "orgId", ORG_ID.toString(),
                "scopeLevel", "ORGANIZATION"
        ), request());

        assertThat(context.subject().subjectId()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(context.attributes().get(StandardAttributeKeys.USER_ID, UUID.class)).isEmpty();
        assertThat(context.tenant().spaceId()).isNull();
    }

    @Test
    void j3_changingSpace_keepsSubjectAndChangesOnlyUserAndSpace() {
        TakiboSecurityContext first = TakiboSecurityContextFactory.from(spaceClaims(USER_ID, SPACE_ID), request());
        TakiboSecurityContext second = TakiboSecurityContextFactory.from(spaceClaims(OTHER_USER_ID, OTHER_SPACE_ID), request());

        assertThat(second.subject().subjectId()).isEqualTo(first.subject().subjectId());
        assertThat(first.attributes().get(StandardAttributeKeys.USER_ID, UUID.class)).contains(USER_ID);
        assertThat(second.attributes().get(StandardAttributeKeys.USER_ID, UUID.class)).contains(OTHER_USER_ID);
        assertThat(second.tenant().spaceId()).isEqualTo(OTHER_SPACE_ID.toString());
    }

    @Test
    void j4_subDifferentFromAccountId_isRejected() {
        Map<String, Object> claims = new java.util.HashMap<>(spaceClaims(USER_ID, SPACE_ID));
        claims.put("sub", OTHER_ACCOUNT_ID.toString());

        assertThatThrownBy(() -> TakiboSecurityContextFactory.from(claims, request()))
                .isInstanceOf(InvalidTakiboSecurityContextException.class)
                .hasMessage("Human token sub must equal account_id");
    }

    @Test
    void j5_userWithoutSpace_orSpaceWithoutUser_isRejected() {
        Map<String, Object> userOnly = new java.util.HashMap<>(spaceClaims(USER_ID, SPACE_ID));
        userOnly.remove("spaceId");
        Map<String, Object> spaceOnly = new java.util.HashMap<>(spaceClaims(USER_ID, SPACE_ID));
        spaceOnly.remove("userId");

        assertThatThrownBy(() -> TakiboSecurityContextFactory.from(userOnly, request()))
                .isInstanceOf(InvalidTakiboSecurityContextException.class);
        assertThatThrownBy(() -> TakiboSecurityContextFactory.from(spaceOnly, request()))
                .isInstanceOf(InvalidTakiboSecurityContextException.class);
    }

    @Test
    void j5_accountWithoutOrganization_isRejected() {
        assertThatThrownBy(() -> TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "HUMAN",
                "sub", ACCOUNT_ID.toString(),
                "accountId", ACCOUNT_ID.toString()
        ), request()))
                .isInstanceOf(InvalidTakiboSecurityContextException.class)
                .hasMessage("Human token with account_id must carry org_id");
    }

    @Test
    void j6_malformedIdentityClaims_areRejectedWithTheContextException() {
        for (String key : List.of("accountId", "userId", "orgId", "spaceId")) {
            Map<String, Object> claims = new java.util.HashMap<>(spaceClaims(USER_ID, SPACE_ID));
            claims.put(key, "not-a-uuid");

            assertThatThrownBy(() -> TakiboSecurityContextFactory.from(claims, request()))
                    .as(key)
                    .isInstanceOf(InvalidTakiboSecurityContextException.class)
                    .hasMessage("Malformed identity claim: " + key);
        }
    }

    @Test
    void j7_serviceWithUuidSubject_isServiceAndCarriesNoHumanAttributes() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "CLIENT_APP",
                "sub", USER_ID.toString()
        ), request());

        assertThat(context.subject().nature()).isEqualTo(SubjectNature.SERVICE);
        assertThat(context.attributes().get(StandardAttributeKeys.USER_ID, UUID.class)).isEmpty();
        assertThat(context.attributes().get(StandardAttributeKeys.ACCOUNT_ID, UUID.class)).isEmpty();
    }

    @Test
    void legacyHumanWithoutAccountId_keepsHistoricalSubjectAndIsNeverAnAccount() {
        TakiboSecurityContext context = TakiboSecurityContextFactory.from(Map.of(
                "subjectType", "HUMAN",
                "sub", USER_ID.toString(),
                "userId", USER_ID.toString()
        ), request());

        assertThat(context.subject().subjectId()).isEqualTo(USER_ID.toString());
        assertThat(context.attributes().get(StandardAttributeKeys.ACCOUNT_ID, UUID.class)).isEmpty();
    }

    private Map<String, Object> spaceClaims(UUID userId, UUID spaceId) {
        return Map.of(
                "subjectType", "HUMAN",
                "authMethod", "PASSWORD",
                "sub", ACCOUNT_ID.toString(),
                "accountId", ACCOUNT_ID.toString(),
                "userId", userId.toString(),
                "orgId", ORG_ID.toString(),
                "spaceId", spaceId.toString(),
                "scopeLevel", "SPACE"
        );
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        return request;
    }
}
