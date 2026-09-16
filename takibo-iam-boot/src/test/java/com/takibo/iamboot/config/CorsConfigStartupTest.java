package com.takibo.iamboot.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Démarrage du contexte selon le profil (SEC-TMS-05, AC-08 à AC-11). Le comportement HTTP sur
 * les vraies chaînes Spring Security est prouvé par {@code CorsPolicyIntegrationTest}.
 */
class CorsConfigStartupTest {

    private static final List<String> FORBIDDEN_OUTSIDE_DEV = List.of(
            "*",
            "https://*.example.test",
            "http://localhost:*",
            "null",
            "https://app.example.test/cb",
            "https://app.example.test?x=1",
            "https://app.example.test#frag",
            "https://user@app.example.test",
            "http://app.example.test");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CorsConfig.class);

    @Test
    void ac08_outsideDev_aForbiddenValuePreventsStartup() {
        for (String profile : new String[]{null, "test", "ci"}) {
            for (String value : FORBIDDEN_OUTSIDE_DEV) {
                withProfile(profile)
                        .withPropertyValues(CorsAllowedOrigins.PROPERTY + "=" + value)
                        .run(context -> {
                            assertThat(context).as("profil %s, valeur %s", profile, value).hasFailed();
                            assertThat(context).getFailure().rootCause()
                                    .hasMessageContaining(CorsAllowedOrigins.PROPERTY)
                                    .hasMessageContaining("« " + value + " »");
                        });
            }
        }
    }

    @Test
    void ac09_devProfile_acceptsPatternAndJokerAndServesMatchingOrigin() {
        withProfile("dev")
                .withPropertyValues(CorsAllowedOrigins.PROPERTY + "=http://localhost:[*]")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MockHttpServletResponse response = preflight(
                            context.getBean(CorsConfigurationSource.class), "http://localhost:5173", "POST");
                    assertThat(response.getStatus()).isEqualTo(200);
                    assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                            .isEqualTo("http://localhost:5173");
                    assertThat(preflight(context.getBean(CorsConfigurationSource.class),
                            "http://evil.example.test", "POST").getStatus()).isEqualTo(403);
                });

        withProfile("dev")
                .withPropertyValues(CorsAllowedOrigins.PROPERTY + "=*")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MockHttpServletResponse response = preflight(
                            context.getBean(CorsConfigurationSource.class), "https://anywhere.example.test", "POST");
                    assertThat(response.getStatus()).isEqualTo(200);
                    assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
                });
    }

    @Test
    void ac10_emptyList_startsAndClosesCrossOriginsWithoutTouchingSameOriginCalls() {
        for (String profile : new String[]{null, "test", "ci", "dev"}) {
            withProfile(profile)
                    .withPropertyValues(CorsAllowedOrigins.PROPERTY + "=")
                    .run(context -> {
                        assertThat(context).as("profil %s", profile).hasNotFailed();
                        CorsConfigurationSource source = context.getBean(CorsConfigurationSource.class);

                        MockHttpServletResponse crossOrigin = preflight(source, "https://portail.example.test", "POST");
                        assertThat(crossOrigin.getStatus()).isEqualTo(403);
                        assertThat(crossOrigin.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();

                        MockHttpServletRequest noOrigin = new MockHttpServletRequest("GET", "/api/v1/users");
                        MockHttpServletResponse sameOrigin = new MockHttpServletResponse();
                        assertThat(new DefaultCorsProcessor().processRequest(
                                source.getCorsConfiguration(noOrigin), noOrigin, sameOrigin)).isTrue();
                        assertThat(sameOrigin.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
                    });
        }
    }

    @Test
    void ac11_credentialsAreNeverAllowed() {
        withProfile("dev")
                .withPropertyValues(CorsAllowedOrigins.PROPERTY + "=*")
                .run(context -> assertThat(corsConfiguration(context.getBean(CorsConfigurationSource.class))
                        .getAllowCredentials()).isFalse());
        withProfile(null)
                .withPropertyValues(CorsAllowedOrigins.PROPERTY + "=https://portail.example.test")
                .run(context -> assertThat(corsConfiguration(context.getBean(CorsConfigurationSource.class))
                        .getAllowCredentials()).isFalse());
    }

    @Test
    void sourceIsNotAUrlBasedSource_soNoChainInheritsItImplicitly() {
        runner.run(context -> assertThat(context.getBeanNamesForType(
                org.springframework.web.cors.UrlBasedCorsConfigurationSource.class)).isEmpty());
    }

    private ApplicationContextRunner withProfile(String profile) {
        return profile == null
                ? runner
                : runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile));
    }

    private static CorsConfiguration corsConfiguration(CorsConfigurationSource source) {
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", "/"));
    }

    private static MockHttpServletResponse preflight(CorsConfigurationSource source, String origin, String method)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response);
        return response;
    }
}
