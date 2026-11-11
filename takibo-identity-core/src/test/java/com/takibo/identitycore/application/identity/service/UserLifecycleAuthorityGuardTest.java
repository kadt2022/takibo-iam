package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.rbac.effective.model.EffectiveRbac;
import com.takibo.identitycore.application.rbac.effective.port.in.EffectiveRbacQueryCase;
import com.takibo.identitycore.domain.exception.SelfDemotionException;
import com.takibo.identitycore.domain.exception.UserLifecycleAuthorityException;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLifecycleAuthorityGuardTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACTOR = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("cccccccc-0000-0000-0000-000000000002");
    private static final ResolvedSpaceKey KEY = new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock private CurrentAccountContextCase currentAccountContext;
    @Mock private EffectiveRbacQueryCase effectiveRbac;

    @InjectMocks private UserLifecycleAuthorityGuard guard;

    @BeforeEach
    void actorIsAuthenticated() {
        lenient().when(currentAccountContext.requireCurrentAccountId()).thenReturn(ACTOR);
    }

    @ParameterizedTest(name = "auteur [{0}] restreint cible [{1}]")
    @CsvSource({
            "R_SPACE_USER_ADMIN,         R_SPACE_AUDITOR",
            "R_SPACE_USER_ADMIN,         ''",
            "R_SPACE_ADMIN,              R_SPACE_ADMIN",
            "R_SPACE_ADMIN,              R_SPACE_USER_ADMIN",
            "R_ORG_ADMIN,                R_ORG_ADMIN",
            "R_ORG_ADMIN,                R_SPACE_ADMIN",
            "R_ORG_OWNER R_SPACE_ADMIN,  R_ORG_ADMIN",
    })
    void an_actor_restricts_a_target_of_equal_or_lower_authority(String actorRoles, String targetRoles) {
        givenRoles(ACTOR, actorRoles);
        givenRoles(TARGET, targetRoles);

        assertThatCode(() -> guard.assertMayRestrict(KEY, target())).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "auteur [{0}] ne restreint pas cible [{1}]")
    @CsvSource({
            "R_SPACE_USER_ADMIN,  R_SPACE_ADMIN",
            "R_SPACE_USER_ADMIN,  R_ORG_ADMIN",
            "R_SPACE_ADMIN,       R_ORG_ADMIN",
            "R_ORG_USER_ADMIN,    R_SPACE_ADMIN",
            "R_SPACE_USER_ADMIN,  R_ORG_OWNER",
            "R_SPACE_ADMIN,       R_ORG_OWNER R_ORG_ADMIN R_SPACE_ADMIN",
            "R_ORG_ADMIN,         R_ORG_OWNER",
    })
    void an_actor_never_restricts_a_higher_authority_nor_the_owner(String actorRoles, String targetRoles) {
        lenient().when(effectiveRbac.effectiveFor(ORG_ID, SPACE_ID, ACTOR)).thenReturn(rbac(actorRoles));
        givenRoles(TARGET, targetRoles);

        assertThatThrownBy(() -> guard.assertMayRestrict(KEY, target()))
                .isInstanceOf(UserLifecycleAuthorityException.class);
    }

    @Test
    void the_target_authority_is_the_effective_one_including_inherited_roles() {
        givenRoles(ACTOR, "R_SPACE_USER_ADMIN");
        // R_SPACE_ADMIN hérité de G_SPACE_ADMINS : l'EffectiveRbac le porte comme un rôle direct.
        when(effectiveRbac.effectiveFor(ORG_ID, SPACE_ID, TARGET))
                .thenReturn(new EffectiveRbac(List.of("R_SPACE_ADMIN"), List.of("G_SPACE_ADMINS"), List.of()));

        assertThatThrownBy(() -> guard.assertMayRestrict(KEY, target()))
                .isInstanceOf(UserLifecycleAuthorityException.class);
    }

    @Test
    void nobody_restricts_themselves() {
        User self = mock(User.class);
        when(self.getAccountId()).thenReturn(AccountId.of(ACTOR));

        assertThatThrownBy(() -> guard.assertMayRestrict(KEY, self)).isInstanceOf(SelfDemotionException.class);
    }

    private void givenRoles(UUID accountId, String roles) {
        when(effectiveRbac.effectiveFor(ORG_ID, SPACE_ID, accountId)).thenReturn(rbac(roles));
    }

    private static EffectiveRbac rbac(String roles) {
        List<String> codes = roles == null || roles.isBlank()
                ? List.of() : Arrays.asList(roles.trim().split("\\s+"));
        return new EffectiveRbac(codes, List.of(), List.of());
    }

    private static User target() {
        User user = mock(User.class);
        lenient().when(user.getAccountId()).thenReturn(AccountId.of(TARGET));
        lenient().when(user.getId()).thenReturn(new UserId(UUID.fromString("dddddddd-0000-0000-0000-000000000002")));
        return user;
    }
}
