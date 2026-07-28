package com.takibo.iamboot.security;

import com.takibo.authorizationserver.infrastructure.springauthserver.properties.TasAuthorizationServerProperties;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.HumanTokenSigner;
import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.rbac.effective.model.EffectivePermissionRequest;
import com.takibo.identitycore.application.rbac.effective.model.PermissionCode;
import com.takibo.identitycore.application.rbac.effective.model.RbacActorSource;
import com.takibo.identitycore.application.rbac.effective.model.RbacSubjectNature;
import com.takibo.identitycore.application.rbac.effective.model.SituatedTechnicalRole;
import com.takibo.identitycore.application.rbac.effective.service.EffectivePermissionResolver;
import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;
import com.takibo.identitycore.domain.catalogrbac.RolePermissionCatalog;
import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.port.SpaceManagementCase;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SituatedHumanTokenPipelineIntegrationTest {

    private static final UUID ORG_ID =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID FIRST_SPACE_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID SECOND_SPACE_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000099");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID FIRST_USER_ID =
            UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID SECOND_USER_ID =
            UUID.fromString("dddddddd-0000-0000-0000-000000000099");

    @Test
    void selectingAnotherSpace_recalculatesThenSignsACompleteSituatedSnapshot() {
        SpaceManagementCase spaces = mock(SpaceManagementCase.class);
        when(spaces.findOrgIdBySpaceId(SpaceId.of(FIRST_SPACE_ID)))
                .thenReturn(Optional.of(ORG_ID));
        when(spaces.findOrgIdBySpaceId(SpaceId.of(SECOND_SPACE_ID)))
                .thenReturn(Optional.of(ORG_ID));
        EffectivePermissionResolver resolver = new EffectivePermissionResolver(
                new RolePermissionCatalog(), spaces);
        CapturingJwtEncoder encoder = new CapturingJwtEncoder();
        BootHumanAccessTokenIssuer issuer =
                new BootHumanAccessTokenIssuer(realSigner(encoder));

        Set<SituatedTechnicalRole> firstRoles = Set.of(
                new SituatedTechnicalRole(TechnicalRole.ORG_USER_ADMIN, ORG_ID, null),
                new SituatedTechnicalRole(
                        TechnicalRole.SPACE_CLIENT_ADMIN, ORG_ID, FIRST_SPACE_ID));
        List<String> firstPermissions =
                resolveForSpace(resolver, FIRST_SPACE_ID, firstRoles);
        HumanTokenRequest firstRequest = HumanTokenRequest.spaceScoped(
                ORG_ID,
                FIRST_SPACE_ID,
                ACCOUNT_ID,
                FIRST_USER_ID,
                List.of("R_ORG_USER_ADMIN", "R_SPACE_CLIENT_ADMIN"),
                List.of(),
                firstPermissions);

        Set<SituatedTechnicalRole> secondRoles = Set.of(
                new SituatedTechnicalRole(TechnicalRole.ORG_USER_ADMIN, ORG_ID, null));
        List<String> secondPermissions =
                resolveForSpace(resolver, SECOND_SPACE_ID, secondRoles);
        HumanTokenRequest secondRequest = HumanTokenRequest.spaceScoped(
                ORG_ID,
                SECOND_SPACE_ID,
                ACCOUNT_ID,
                SECOND_USER_ID,
                List.of("R_ORG_USER_ADMIN"),
                List.of(),
                secondPermissions);

        issuer.issue(firstRequest);
        issuer.issue(secondRequest);
        JwtClaimsSet firstClaims = encoder.encodedClaims.get(0);
        JwtClaimsSet secondClaims = encoder.encodedClaims.get(1);

        assertThat(firstClaims.getClaimAsString("space_id"))
                .isEqualTo(FIRST_SPACE_ID.toString());
        assertThat(secondClaims.getClaimAsString("space_id"))
                .isEqualTo(SECOND_SPACE_ID.toString());
        assertThat(firstClaims.getClaimAsString("org_id"))
                .isEqualTo(ORG_ID.toString());
        assertThat(secondClaims.getClaimAsString("org_id"))
                .isEqualTo(ORG_ID.toString());
        assertThat(firstClaims.getClaimAsString("takibo_scope_level")).isEqualTo("SPACE");
        assertThat(secondClaims.getClaimAsString("takibo_scope_level")).isEqualTo("SPACE");
        assertThat(firstClaims.getClaimAsString("takibo_tenant_source"))
                .isEqualTo("human_space_selection");
        assertThat(secondClaims.getClaimAsString("takibo_tenant_source"))
                .isEqualTo("human_space_selection");

        assertThat(firstClaims.getClaimAsStringList("permissions"))
                .contains("P_SPACE_CLIENTS_MANAGE")
                .allMatch(permission -> permission.startsWith("P_SPACE_"));
        assertThat(secondClaims.getClaimAsStringList("permissions"))
                .doesNotContain("P_SPACE_CLIENTS_MANAGE")
                .allMatch(permission -> permission.startsWith("P_SPACE_"));
        assertThat(firstClaims.getClaimAsStringList("roles"))
                .containsExactly("R_ORG_USER_ADMIN", "R_SPACE_CLIENT_ADMIN");
        assertThat(secondClaims.getClaimAsStringList("roles"))
                .containsExactly("R_ORG_USER_ADMIN");
    }

    private List<String> resolveForSpace(
            EffectivePermissionResolver resolver,
            UUID spaceId,
            Set<SituatedTechnicalRole> roles
    ) {
        EffectivePermissionRequest request = new EffectivePermissionRequest(
                roles,
                Set.of(),
                AuthorityPlan.SPACE,
                ORG_ID,
                spaceId,
                RbacSubjectNature.HUMAN,
                RbacActorSource.HUMAN);
        return resolver.resolve(request).stream()
                .map(PermissionCode::code)
                .toList();
    }

    private HumanTokenSigner realSigner(JwtEncoder encoder) {
        TasAuthorizationServerProperties properties =
                new TasAuthorizationServerProperties();
        properties.setIssuer("http://localhost:8081");
        return new HumanTokenSigner(encoder, properties, 300, 600);
    }

    private static final class CapturingJwtEncoder implements JwtEncoder {

        private final List<JwtClaimsSet> encodedClaims = new ArrayList<>();

        @Override
        public Jwt encode(JwtEncoderParameters parameters) {
            JwtClaimsSet claims = parameters.getClaims();
            encodedClaims.add(claims);
            return Jwt.withTokenValue("captured-token-" + encodedClaims.size())
                    .header("alg", "none")
                    .claims(target -> target.putAll(claims.getClaims()))
                    .issuedAt(claims.getIssuedAt())
                    .expiresAt(claims.getExpiresAt())
                    .build();
        }
    }
}
