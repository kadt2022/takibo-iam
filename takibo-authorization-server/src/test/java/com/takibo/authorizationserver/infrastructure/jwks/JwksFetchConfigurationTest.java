package com.takibo.authorizationserver.infrastructure.jwks;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une configuration dangereuse fait échouer le démarrage : elle n'est jamais ignorée en silence.
 */
class JwksFetchConfigurationTest {

    private static final String PREFIX = "takibo.oauth.jwks-fetch.";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwksFetchConfiguration.class);

    @Test
    void defaults_start_and_refuse_http_and_internal_destinations() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            JwksFetchClient client = context.getBean(JwksFetchClient.class);
            assertThatThrownBy(() -> client.checkUri(URI.create("http://keys.example.com/jwks")))
                    .isInstanceOf(DestinationRefusedException.class);
            assertThatThrownBy(() -> client.checkUri(URI.create("https://localhost/jwks")))
                    .isInstanceOf(DestinationRefusedException.class);
        });
    }

    @Test
    void http_outside_the_dev_profile_prevents_startup() {
        runner.withPropertyValues(PREFIX + "trusted-hosts=localhost", PREFIX + "allow-http-for-trusted-hosts=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("reserved to the 'dev' profile"));
    }

    @Test
    void http_under_another_active_profile_still_prevents_startup() {
        runner.withPropertyValues(PREFIX + "trusted-hosts=localhost", PREFIX + "allow-http-for-trusted-hosts=true",
                        "spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void http_under_the_dev_profile_reaches_trusted_names_only() {
        runner.withPropertyValues(PREFIX + "trusted-hosts=localhost,keycloak",
                        PREFIX + "allow-http-for-trusted-hosts=true", "spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwksFetchClient client = context.getBean(JwksFetchClient.class);
                    assertThatCode(() -> client.checkUri(URI.create(
                            "http://keycloak:8080/realms/takibo/protocol/openid-connect/certs")))
                            .doesNotThrowAnyException();
                    assertThatThrownBy(() -> client.checkUri(URI.create("http://keys.example.com/jwks")))
                            .isInstanceOf(DestinationRefusedException.class);
                });
    }

    @Test
    void a_trusted_range_overlapping_a_forbidden_one_prevents_startup() {
        runner.withPropertyValues(PREFIX + "trusted-cidrs=169.254.0.0/16")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("always-forbidden"));
    }

    @Test
    void a_wildcard_trusted_host_prevents_startup() {
        runner.withPropertyValues(PREFIX + "trusted-hosts=*.internal")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void a_non_positive_limit_prevents_startup() {
        runner.withPropertyValues(PREFIX + "total-timeout=0s")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("total-timeout"));
        runner.withPropertyValues(PREFIX + "max-response-size=0B")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("max-response-size"));
    }
}
