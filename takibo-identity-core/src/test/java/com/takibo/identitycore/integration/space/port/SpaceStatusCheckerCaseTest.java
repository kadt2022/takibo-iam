package com.takibo.identitycore.integration.space.port;

import com.takibo.identitycore.domain.exception.SpaceNotActiveException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.domain.status.SpaceOperationalStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat fail-closed des méthodes par défaut du port : un statut inconnu n'est
 * jamais interprété comme ACTIVE.
 */
class SpaceStatusCheckerCaseTest {

    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private static SpaceStatusCheckerCase returning(SpaceOperationalStatus status) {
        return spaceId -> Optional.ofNullable(status);
    }

    @Test
    void isActive_activeSpace_isTrue() {
        assertThat(returning(SpaceOperationalStatus.ACTIVE).isActive(SPACE_ID)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SpaceOperationalStatus.class, names = {"SUSPENDED", "DISABLED"})
    void isActive_nonActiveSpace_isFalse(SpaceOperationalStatus status) {
        assertThat(returning(status).isActive(SPACE_ID)).isFalse();
    }

    @Test
    void isActive_unknownSpace_isFalse_failClosed() {
        assertThat(returning(null).isActive(SPACE_ID)).isFalse();
    }

    @Test
    void assertSpaceExistsAndActive_activeSpace_passes() {
        SpaceStatusCheckerCase checker = returning(SpaceOperationalStatus.ACTIVE);

        assertThatCode(() -> checker.assertSpaceExistsAndActive(SPACE_ID))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = SpaceOperationalStatus.class, names = {"SUSPENDED", "DISABLED"})
    void assertSpaceExistsAndActive_nonActiveSpace_isNotActive(SpaceOperationalStatus status) {
        SpaceStatusCheckerCase checker = returning(status);

        assertThatThrownBy(() -> checker.assertSpaceExistsAndActive(SPACE_ID))
                .isInstanceOfSatisfying(SpaceNotActiveException.class,
                        e -> assertThat(e.getSpaceId()).isEqualTo(SPACE_ID));
    }

    @Test
    void assertSpaceExistsAndActive_unknownSpace_isNotFound() {
        SpaceStatusCheckerCase checker = returning(null);

        assertThatThrownBy(() -> checker.assertSpaceExistsAndActive(SPACE_ID))
                .isInstanceOf(SpaceNotFoundException.class)
                .hasMessageContaining(SPACE_ID.toString());
    }
}
