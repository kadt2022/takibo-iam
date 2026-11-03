package com.takibo.identitycore.integration.space.annotations;

import com.takibo.identitycore.domain.exception.SpaceGuardException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.domain.status.SpaceGuardStatus;
import com.takibo.identitycore.domain.status.SpaceOperationalStatus;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Garde {@code @RequireActiveSpace} appliquée par un vrai proxy AspectJ : prouve à la
 * fois le pointcut (annotation de classe / de méthode) et la décision fail-closed
 * (space absent, suspendu, désactivé, identifiant introuvable).
 */
@ExtendWith(MockitoExtension.class)
class SpaceActiveAspectTest {

    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock
    private SpaceStatusCheckerCase spaceStatusChecker;

    private SpaceActiveAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new SpaceActiveAspect(spaceStatusChecker);
        ReflectionTestUtils.setField(aspect, "enabled", true);
    }

    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    private void spaceIs(UUID spaceId, SpaceOperationalStatus status) {
        when(spaceStatusChecker.findStatus(spaceId)).thenReturn(Optional.of(status));
    }

    @Nested
    class StatusDecision {

        @Test
        void activeSpace_letsTheCallThrough() {
            spaceIs(SPACE_ID, SpaceOperationalStatus.ACTIVE);
            ClassGuarded target = new ClassGuarded();

            assertThat(proxy(target).orgAndSpace(ORG_ID, SPACE_ID)).isEqualTo("done");
            assertThat(target.received).containsExactly(ORG_ID, SPACE_ID);
        }

        @Test
        void suspendedSpace_isRejectedWithSuspendedCode_andTargetIsNeverInvoked() {
            spaceIs(SPACE_ID, SpaceOperationalStatus.SUSPENDED);
            ClassGuarded target = new ClassGuarded();
            ClassGuarded guarded = proxy(target);

            assertThatThrownBy(() -> guarded.orgAndSpace(ORG_ID, SPACE_ID))
                    .isInstanceOfSatisfying(SpaceGuardException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(SpaceGuardStatus.SPACE_SUSPENDED);
                        assertThat(e.getSpaceId()).isEqualTo(SPACE_ID);
                    });
            assertThat(target.invocations).isZero();
        }

        @Test
        void disabledSpace_isRejectedWithDisabledCode_andTargetIsNeverInvoked() {
            spaceIs(SPACE_ID, SpaceOperationalStatus.DISABLED);
            ClassGuarded target = new ClassGuarded();
            ClassGuarded guarded = proxy(target);

            assertThatThrownBy(() -> guarded.orgAndSpace(ORG_ID, SPACE_ID))
                    .isInstanceOfSatisfying(SpaceGuardException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(SpaceGuardStatus.SPACE_DISABLED);
                        assertThat(e.getSpaceId()).isEqualTo(SPACE_ID);
                    });
            assertThat(target.invocations).isZero();
        }

        @Test
        void unknownSpace_isNotFound_failClosed() {
            when(spaceStatusChecker.findStatus(SPACE_ID)).thenReturn(Optional.empty());
            ClassGuarded target = new ClassGuarded();
            ClassGuarded guarded = proxy(target);

            assertThatThrownBy(() -> guarded.orgAndSpace(ORG_ID, SPACE_ID))
                    .isInstanceOf(SpaceNotFoundException.class)
                    .hasMessageContaining(SPACE_ID.toString());
            assertThat(target.invocations).isZero();
        }

        @Test
        void noResolvableSpaceId_isRefused_withoutQueryingTheChecker() {
            ClassGuarded target = new ClassGuarded();
            ClassGuarded guarded = proxy(target);

            assertThatThrownBy(() -> guarded.noSpace("not-a-uuid"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("spaceId introuvable");
            verifyNoInteractions(spaceStatusChecker);
            assertThat(target.invocations).isZero();
        }

        @Test
        void disabledPolicy_skipsTheGuardEntirely() {
            ReflectionTestUtils.setField(aspect, "enabled", false);

            ClassGuarded target = new ClassGuarded();

            assertThat(proxy(target).noSpace("not-a-uuid")).isEqualTo("done");
            assertThat(target.received).containsExactly("not-a-uuid");
            verifyNoInteractions(spaceStatusChecker);
        }
    }

    @Nested
    class SpaceIdResolution {

        @Test
        void defaultKeys_checkTheSpaceIdPathVariable_notTheOrgIdThatPrecedesIt() {
            spaceIs(SPACE_ID, SpaceOperationalStatus.ACTIVE);
            ClassGuarded target = new ClassGuarded();

            proxy(target).orgAndSpace(ORG_ID, SPACE_ID);

            assertThat(target.received).containsExactly(ORG_ID, SPACE_ID);
            verify(spaceStatusChecker).findStatus(SPACE_ID);
            verify(spaceStatusChecker, never()).findStatus(ORG_ID);
        }

        @Test
        void sidAlias_declaredThroughValue_isParsedFromString() {
            spaceIs(SPACE_ID, SpaceOperationalStatus.ACTIVE);

            ClassGuarded target = new ClassGuarded();

            proxy(target).sidAsString(SPACE_ID.toString());

            assertThat(target.received).containsExactly(SPACE_ID.toString());
            verify(spaceStatusChecker).findStatus(SPACE_ID);
        }

        @Test
        void commandArgument_exposingSpaceId_isUsedWhenNoPathVariableMatches() {
            spaceIs(SPACE_ID, SpaceOperationalStatus.SUSPENDED);
            ClassGuarded target = new ClassGuarded();
            ClassGuarded guarded = proxy(target);
            SpacedCommand command = new SpacedCommand(SPACE_ID);

            assertThatThrownBy(() -> guarded.command(command))
                    .isInstanceOf(SpaceGuardException.class);
            verify(spaceStatusChecker).findStatus(SPACE_ID);
            assertThat(target.received).isEmpty();
        }

        @Test
        void commandArgument_whoseSpaceIdAccessorFails_isIgnored_andCallIsRefused() {
            ClassGuarded target = new ClassGuarded();
            ClassGuarded guarded = proxy(target);
            FailingCommand command = new FailingCommand();

            assertThatThrownBy(() -> guarded.failingCommand(command))
                    .isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(spaceStatusChecker);
            assertThat(target.received).isEmpty();
        }

        @Test
        void invalidUuidPathVariable_isNotTreatedAsASpace_andCallIsRefused() {
            ClassGuarded target = new ClassGuarded();
            ClassGuarded guarded = proxy(target);

            assertThatThrownBy(() -> guarded.sidAsString("finance"))
                    .isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(spaceStatusChecker);
            assertThat(target.received).isEmpty();
        }

        @Test
        void methodAnnotation_customKeys_selectTheConfiguredPathVariable() {
            spaceIs(OTHER_ID, SpaceOperationalStatus.DISABLED);
            MethodGuarded guarded = proxy(new MethodGuarded());

            assertThatThrownBy(() -> guarded.customKey(SPACE_ID, OTHER_ID))
                    .isInstanceOfSatisfying(SpaceGuardException.class,
                            e -> assertThat(e.getSpaceId()).isEqualTo(OTHER_ID));
            verify(spaceStatusChecker, never()).findStatus(SPACE_ID);
        }

        @Test
        void unannotatedMethodOfUnannotatedClass_isNotIntercepted() {
            MethodGuarded target = new MethodGuarded();

            assertThat(proxy(target).unguarded(SPACE_ID)).isEqualTo("done");
            assertThat(target.received).containsExactly(SPACE_ID);
            verify(spaceStatusChecker, never()).findStatus(any());
        }
    }

    // ───────────────────────────── fixtures ─────────────────────────────

    /**
     * Cibles du proxy : leurs signatures (noms de path variables, types d'arguments)
     * sont précisément ce que l'aspect lit. Chaque méthode enregistre les arguments
     * reçus, ce qui prouve que l'appel autorisé atteint la cible avec ses valeurs
     * intactes, et qu'un appel refusé ne l'atteint jamais.
     */
    @RequireActiveSpace
    static class ClassGuarded {
        int invocations;
        final List<Object> received = new ArrayList<>();

        public String orgAndSpace(@PathVariable("orgId") UUID orgId, @PathVariable("spaceId") UUID spaceId) {
            return done(orgId, spaceId);
        }

        public String sidAsString(@PathVariable(value = "sid") String sid) {
            return done(sid);
        }

        public String command(SpacedCommand command) {
            return done(command);
        }

        public String failingCommand(FailingCommand command) {
            return done(command);
        }

        public String noSpace(String label) {
            return done(label);
        }

        private String done(Object... args) {
            invocations++;
            received.addAll(Arrays.asList(args));
            return "done";
        }
    }

    static class MethodGuarded {
        final List<Object> received = new ArrayList<>();

        @RequireActiveSpace(pathVarKeys = "tenantSpace")
        public String customKey(@PathVariable("spaceId") UUID notTheGuardedOne,
                                @PathVariable("tenantSpace") UUID guarded) {
            received.addAll(Arrays.asList(notTheGuardedOne, guarded));
            return "done";
        }

        public String unguarded(UUID spaceId) {
            received.add(spaceId);
            return "done";
        }
    }

    public record SpacedCommand(UUID spaceId) {
    }

    public static class FailingCommand {
        public UUID spaceId() {
            throw new IllegalStateException("boom");
        }
    }
}
