package com.takibo.iamboot.tas;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TasPostgresBaselineCiGuardTest {

    @Test
    void withoutDocker_inCi_theSuiteFailsInsteadOfSkipping() {
        assertThatThrownBy(() -> TasPostgresBaseline.requiredInCi(false, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Docker indisponible en CI");
    }

    @Test
    void withoutDocker_outsideCi_theTestsAreSkipped() {
        assertThat(TasPostgresBaseline.requiredInCi(false, null)).isFalse();
        assertThat(TasPostgresBaseline.requiredInCi(false, "false")).isFalse();
    }

    @Test
    void withDocker_theTestsRun_inCiOrNot() {
        assertThat(TasPostgresBaseline.requiredInCi(true, "true")).isTrue();
        assertThat(TasPostgresBaseline.requiredInCi(true, null)).isTrue();
    }
}
