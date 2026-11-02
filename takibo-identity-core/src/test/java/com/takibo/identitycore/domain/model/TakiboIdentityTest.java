package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.TakiboIdentityId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Identité d'organisation : ancre (org, account) immuable, statut et signaux de
 * confiance modifiés par copie uniquement.
 */
class TakiboIdentityTest {

    private static final TakiboIdentityId ID =
            TakiboIdentityId.of(UUID.fromString("cccccccc-0000-0000-0000-000000000003"));
    private static final OrganizationId ORG =
            OrganizationId.of(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"));
    private static final AccountId ACCOUNT =
            AccountId.of(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"));

    interface Factory {
        TakiboIdentity create(TakiboIdentityId id, OrganizationId orgId, AccountId accountId);
    }

    static Stream<Arguments> factories() {
        return Stream.of(
                Arguments.of((Factory) TakiboIdentity::createHuman, IdentityType.HUMAN),
                Arguments.of((Factory) TakiboIdentity::createService, IdentityType.SERVICE),
                Arguments.of((Factory) TakiboIdentity::createMachine, IdentityType.MACHINE));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void factory_createsAnActiveUntrustedIdentityOfItsType(Factory factory, IdentityType expectedType) {
        TakiboIdentity identity = factory.create(ID, ORG, ACCOUNT);

        assertThat(identity.getId()).isEqualTo(ID);
        assertThat(identity.getOrgId()).isEqualTo(ORG);
        assertThat(identity.getAccountId()).isEqualTo(ACCOUNT);
        assertThat(identity.getIdentityType()).isEqualTo(expectedType);
        assertThat(identity.getIdentityStatus()).isEqualTo(IdentityStatus.ACTIVE);
        assertThat(identity.getTrustLevel()).isZero();
        assertThat(identity.getRiskScore()).isZero();
        assertThat(identity.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(identity.getVersion()).isZero();
        assertThat(identity.getCreatedAt()).isNotNull().isEqualTo(identity.getUpdatedAt());
        assertThat(identity.getLastActiveAt()).isNull();
    }

    @ParameterizedTest
    @MethodSource("factories")
    void factory_refusesAnIdentityWithoutItsAnchors(Factory factory, IdentityType ignored) {
        assertThatThrownBy(() -> factory.create(null, ORG, ACCOUNT))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> factory.create(ID, null, ACCOUNT))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("orgId");
        assertThatThrownBy(() -> factory.create(ID, ORG, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("accountId");
    }

    static Stream<Arguments> statusTransitions() {
        return Stream.of(
                Arguments.of((UnaryOperator<TakiboIdentity>) TakiboIdentity::suspend, IdentityStatus.SUSPENDED),
                Arguments.of((UnaryOperator<TakiboIdentity>) TakiboIdentity::disable, IdentityStatus.DISABLED),
                Arguments.of((UnaryOperator<TakiboIdentity>) i -> i.suspend().activate(), IdentityStatus.ACTIVE));
    }

    @ParameterizedTest
    @MethodSource("statusTransitions")
    void statusTransition_returnsACopy_andNeverMovesTheAnchors(UnaryOperator<TakiboIdentity> transition,
                                                               IdentityStatus expected) {
        TakiboIdentity original = TakiboIdentity.createHuman(ID, ORG, ACCOUNT);

        TakiboIdentity changed = transition.apply(original);

        assertThat(changed).isNotSameAs(original);
        assertThat(changed.getIdentityStatus()).isEqualTo(expected);
        assertThat(original.getIdentityStatus()).isEqualTo(IdentityStatus.ACTIVE);
        assertThat(changed.getId()).isEqualTo(ID);
        assertThat(changed.getOrgId()).isEqualTo(ORG);
        assertThat(changed.getAccountId()).isEqualTo(ACCOUNT);
        assertThat(changed.getIdentityType()).isEqualTo(IdentityType.HUMAN);
        assertThat(changed.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(changed.getUpdatedAt()).isAfterOrEqualTo(original.getUpdatedAt());
    }

    @Test
    void trustSignals_areUpdatedByCopy() {
        TakiboIdentity original = TakiboIdentity.createHuman(ID, ORG, ACCOUNT);

        TakiboIdentity scored = original.updateRiskScore(80).updateTrustLevel(3);

        assertThat(scored.getRiskScore()).isEqualTo(80);
        assertThat(scored.getTrustLevel()).isEqualTo(3);
        assertThat(original.getRiskScore()).isZero();
        assertThat(original.getTrustLevel()).isZero();
    }

    @Test
    void recordActivity_stampsLastActivity() {
        Instant before = Instant.now();

        TakiboIdentity active = TakiboIdentity.createHuman(ID, ORG, ACCOUNT).recordActivity();

        assertThat(active.getLastActiveAt()).isAfterOrEqualTo(before);
        assertThat(active.getIdentityStatus()).isEqualTo(IdentityStatus.ACTIVE);
    }

    @Test
    void equality_isCarriedByTheIdentifierOnly() {
        TakiboIdentity human = TakiboIdentity.createHuman(ID, ORG, ACCOUNT);

        assertThat(human).isEqualTo(human.disable()).hasSameHashCodeAs(human.disable());
        assertThat(human).isNotEqualTo(TakiboIdentity.createHuman(TakiboIdentityId.newId(), ORG, ACCOUNT));
    }

    @Test
    void builderDefaults_startWithoutTrustNorRisk() {
        TakiboIdentity built = TakiboIdentity.builder().id(ID).build();

        assertThat(built.getTrustLevel()).isZero();
        assertThat(built.getRiskScore()).isZero();
        assertThat(built.getVersion()).isZero();
    }
}
