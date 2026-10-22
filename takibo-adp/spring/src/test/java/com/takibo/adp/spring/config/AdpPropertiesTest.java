package com.takibo.adp.spring.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdpPropertiesTest {

    @Test
    void defaultsEnableEveryFeatureWithNoopWriter() {
        AdpProperties properties = new AdpProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getTimeoutMs()).isEqualTo(15);
        assertThat(properties.getParallelism()).isEqualTo(8);
        assertThat(properties.getPolicyVersion()).isEqualTo("1.0");
        AdpProperties.Features features = properties.getFeatures();
        assertThat(features.isBaseline()).isTrue();
        assertThat(features.isVelocity()).isTrue();
        assertThat(features.isThresholds()).isTrue();
        assertThat(features.isUncertainty()).isTrue();
        assertThat(features.isLocation()).isTrue();
        assertThat(features.isNetwork()).isTrue();
        assertThat(features.isTime()).isTrue();
        assertThat(properties.getProfile().getWriter()).isEqualTo(AdpProperties.WriterMode.NOOP);
        assertThat(AdpProperties.WriterMode.values())
                .containsExactly(AdpProperties.WriterMode.NOOP, AdpProperties.WriterMode.ASYNC_JPA);
    }

    @Test
    void nestedSectionsCanBeReplaced() {
        AdpProperties properties = new AdpProperties();
        AdpProperties.Features features = new AdpProperties.Features();
        features.setBaseline(false);
        AdpProperties.Profile profile = new AdpProperties.Profile();
        profile.setWriter(AdpProperties.WriterMode.ASYNC_JPA);

        properties.setFeatures(features);
        properties.setProfile(profile);

        assertThat(properties.getFeatures().isBaseline()).isFalse();
        assertThat(properties.getProfile().getWriter()).isEqualTo(AdpProperties.WriterMode.ASYNC_JPA);
    }
}
