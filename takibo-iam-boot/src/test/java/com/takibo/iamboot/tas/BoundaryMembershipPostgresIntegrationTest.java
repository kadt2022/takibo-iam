package com.takibo.iamboot.tas;

import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.securitycontext.model.AttributeKey;
import com.takibo.securitycontext.model.AuthenticationMethod;
import com.takibo.securitycontext.model.ContextAttributeStore;
import com.takibo.securitycontext.model.StandardAttributeKeys;
import com.takibo.securitycontext.model.SubjectIdentity;
import com.takibo.securitycontext.model.SubjectNature;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.model.TemporalContext;
import com.takibo.securitycontext.model.TenantScope;
import com.takibo.securitymanagement.infrastructure.security.JwtAuthenticationToken;
import com.takibo.securitymanagement.infrastructure.security.boundary.BoundaryMembershipService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SEC-TMS-06 : le contrôle d'appartenance joué contre le schéma réel (migrations Flyway sur
 * PostgreSQL 16). Les doublures JDBC des tests unitaires ne voient ni les jointures, ni les
 * types UUID, ni les contraintes : ce test les confronte au moteur.
 * <p>
 * Jeu isolé, UUID tous distincts (jamais userId = accountId) : l'organisation O porte les Spaces
 * S1 et S2 ; l'organisation X porte SX. A a un User dans chacun de S1 et S2 (U1, U2), B un User
 * dans S1 (UB), AX un User dans SX.
 */
@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "security.password-encoder.bcrypt-strength=4"
})
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class BoundaryMembershipPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID ORG_O = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000b1");
    private static final UUID ORG_X = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000b2");
    private static final UUID S1 = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000b1");
    private static final UUID S2 = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000b2");
    private static final UUID SX = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000b3");
    private static final UUID ACCOUNT_A = UUID.fromString("cccccccc-0000-0000-0000-0000000000a1");
    private static final UUID ACCOUNT_B = UUID.fromString("cccccccc-0000-0000-0000-0000000000a2");
    private static final UUID ACCOUNT_AX = UUID.fromString("cccccccc-0000-0000-0000-0000000000a3");
    private static final UUID U1 = UUID.fromString("dddddddd-0000-0000-0000-0000000000c1");
    private static final UUID U2 = UUID.fromString("dddddddd-0000-0000-0000-0000000000c2");
    private static final UUID UB = UUID.fromString("dddddddd-0000-0000-0000-0000000000c3");
    private static final UUID UX = UUID.fromString("dddddddd-0000-0000-0000-0000000000c4");
    private static final UUID UNKNOWN_SPACE = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000ff");

    @Autowired private JdbcTemplate jdbc;

    private BoundaryMembershipService boundary;

    @BeforeEach
    void seed() {
        purge();
        boundary = new BoundaryMembershipService(jdbc);
        organization(ORG_O, "bnd-o");
        organization(ORG_X, "bnd-x");
        space(S1, ORG_O, "bnd-s1");
        space(S2, ORG_O, "bnd-s2");
        space(SX, ORG_X, "bnd-sx");
        account(ACCOUNT_A, ORG_O, "a@bnd.test");
        account(ACCOUNT_B, ORG_O, "b@bnd.test");
        account(ACCOUNT_AX, ORG_X, "ax@bnd.test");
        user(U1, ORG_O, S1, ACCOUNT_A, "u1");
        user(U2, ORG_O, S2, ACCOUNT_A, "u2");
        user(UB, ORG_O, S1, ACCOUNT_B, "ub");
        user(UX, ORG_X, SX, ACCOUNT_AX, "ux");
    }

    @AfterEach
    void cleanUp() {
        purge();
    }

    @Test
    void organizationToken_isRecognizedByItsAccount_forAnySpaceOfItsOrganization() {
        assertThatCode(() -> boundary.assertActorInSpaceOrg(S2, organizationToken(ACCOUNT_A, ORG_O)))
                .doesNotThrowAnyException();
    }

    @Test
    void organizationToken_ofAnotherOrganization_isRefused() {
        assertThatThrownBy(() -> boundary.assertActorInSpaceOrg(S1, organizationToken(ACCOUNT_AX, ORG_X)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_NOT_IN_SPACE_ORG");
    }

    @Test
    void spaceToken_userOfTheTokenSpace_isAccepted_forAnotherSpaceOfTheSameOrganization() {
        // Appartenance organisationnelle seulement : l'acces a S2 reste interdit par
        // SpaceBoundaryGuard (token.space_id), pas par ce controle.
        assertThatCode(() -> boundary.assertActorInSpaceOrg(S2, spaceToken(ACCOUNT_A, U1, S1)))
                .doesNotThrowAnyException();
    }

    @Test
    void j5_userOfAnotherSpaceThanTheTokenSpace_isRefused() {
        // U1 appartient a S1 ; le jeton pretend S2 (meme organisation, meme Account).
        assertThatThrownBy(() -> boundary.assertActorInSpaceOrg(S2, spaceToken(ACCOUNT_A, U1, S2)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_NOT_IN_SPACE_ORG");
    }

    @Test
    void j5_userOfAnotherAccount_isRefused() {
        // UB appartient au compte B ; le jeton annonce le compte A.
        assertThatThrownBy(() -> boundary.assertActorInSpaceOrg(S1, spaceToken(ACCOUNT_A, UB, S1)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_NOT_IN_SPACE_ORG");
    }

    @Test
    void spaceToken_ofAnotherOrganization_isRefusedOnTheTargetOrganization() {
        assertThatThrownBy(() -> boundary.assertActorInSpaceOrg(S1, spaceToken(ACCOUNT_AX, UX, SX)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_NOT_IN_SPACE_ORG");
    }

    @Test
    void absentSpace_isSpaceNotFound_notARefusal() {
        assertThatThrownBy(() -> boundary.assertActorInSpaceOrg(UNKNOWN_SPACE, spaceToken(ACCOUNT_A, U1, S1)))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    @Test
    void orgOwnerBypass_isUnchanged_andSkipsTheUserSpaceInvariant() {
        // Caracterisation : le bypass R_ORG_OWNER (organisation du jeton = cible) sort avant la
        // lecture d'identite, y compris pour une relation U/S incoherente. Non etendu ici.
        Authentication inconsistent = token(ACCOUNT_A, U1, S2, ORG_O, "R_ORG_OWNER");

        assertThatCode(() -> boundary.assertActorInSpaceOrg(S1, inconsistent)).doesNotThrowAnyException();
    }

    // ---------- jetons ----------

    private Authentication organizationToken(UUID accountId, UUID orgId) {
        return token(accountId, null, null, orgId);
    }

    private Authentication spaceToken(UUID accountId, UUID userId, UUID tokenSpaceId) {
        UUID orgId = tokenSpaceId.equals(SX) ? ORG_X : ORG_O;
        return token(accountId, userId, tokenSpaceId, orgId);
    }

    private Authentication token(UUID accountId, UUID userId, UUID spaceId, UUID orgId, String... roles) {
        Map<AttributeKey, Object> attributes = new java.util.LinkedHashMap<>();
        attributes.put(StandardAttributeKeys.ACCOUNT_ID, accountId);
        if (userId != null) {
            attributes.put(StandardAttributeKeys.USER_ID, userId);
        }
        TakiboSecurityContext ctx = TakiboSecurityContext.builder()
                .subject(new SubjectIdentity(accountId.toString(), SubjectNature.HUMAN, Set.of(),
                        AuthenticationMethod.PASSWORD))
                .tenant(new TenantScope(orgId.toString(), spaceId == null ? null : spaceId.toString()))
                .temporal(new TemporalContext(Instant.now(), null, null, 1))
                .attributes(new ContextAttributeStore(attributes))
                .build();
        return new JwtAuthenticationToken("t", accountId.toString(), ctx,
                java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    // ---------- données ----------

    private void organization(UUID id, String code) {
        jdbc.update("INSERT INTO organizations (id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')",
                id, code, code);
    }

    private void space(UUID id, UUID orgId, String code) {
        jdbc.update("INSERT INTO spaces (id, org_id, code, name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                id, orgId, code, code);
    }

    private void account(UUID id, UUID orgId, String email) {
        jdbc.update("INSERT INTO accounts (id, org_id, email, display_name) VALUES (?, ?, ?, ?)",
                id, orgId, email, email);
    }

    private void user(UUID id, UUID orgId, UUID spaceId, UUID accountId, String username) {
        jdbc.update("""
                INSERT INTO users (id, org_id, space_id, account_id, username, status, user_type)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'HUMAN')
                """, id, orgId, spaceId, accountId, username);
    }

    private void purge() {
        jdbc.update("DELETE FROM users WHERE org_id IN (?, ?)", ORG_O, ORG_X);
        jdbc.update("DELETE FROM spaces WHERE org_id IN (?, ?)", ORG_O, ORG_X);
        jdbc.update("DELETE FROM accounts WHERE org_id IN (?, ?)", ORG_O, ORG_X);
        jdbc.update("DELETE FROM organizations WHERE id IN (?, ?)", ORG_O, ORG_X);
    }
}
