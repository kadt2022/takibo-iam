package com.takibo.iamboot.config;

import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsAllowedOriginsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "*",
            "https://*.example.test",
            "http://localhost:*",
            "http://localhost:[*]",
            "null",
            "NULL",
            "https://app.example.test/cb",
            "https://app.example.test?x=1",
            "https://app.example.test#frag",
            "https://user@app.example.test",
            "http://app.example.test",
            "ftp://app.example.test",
            "app.example.test",
            "https://",
            "https://app example.test"
    })
    void outsideDev_refusesEveryValueThatIsNotAnExactOrigin(String value) {
        refusal(false, value)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CorsAllowedOrigins.PROPERTY)
                .hasMessageContaining("« " + value + " »");
    }

    @Test
    void oneInvalidValueRefusesTheWholeList() {
        refusal(false, "https://portail.example.test", "https://*.example.test")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https://*.example.test");
    }

    @Test
    void normalizesToTheSerializedOriginABrowserSends() {
        CorsAllowedOrigins origins = CorsAllowedOrigins.validate(List.of(
                " HTTPS://Portail.Example.Test/ ",
                "https://portail.example.test:443",
                "https://admin.example.test:8443",
                "http://localhost:5173",
                "http://127.0.0.1:80",
                "http://[::1]:3000"), false);

        assertThat(origins.exactOrigins()).containsExactly(
                "https://portail.example.test",
                "https://admin.example.test:8443",
                "http://localhost:5173",
                "http://127.0.0.1",
                "http://[::1]:3000");
        assertThat(origins.originPatterns()).isEmpty();
    }

    @Test
    void httpIsRefusedForHostsThatOnlyLookLikeLoopback() {
        for (String value : List.of(
                "http://127.0.0.1.evil.test",
                "http://localhost.evil.test")) {
            refusal(false, value)
                    .as(value)
                    .hasMessageContaining("loopback");
        }
        // java.net.URI ne reconnaît pas d'hôte dans 127.0.0.256 : refusé avant même la règle HTTP.
        refusal(false, "http://127.0.0.256")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("« http://127.0.0.256 »");
    }

    @Test
    void absentOrBlankListAcceptsNoCrossOrigin() {
        assertThat(CorsAllowedOrigins.validate(null, false).isEmpty()).isTrue();
        assertThat(CorsAllowedOrigins.validate(List.of(), false).isEmpty()).isTrue();
        assertThat(CorsAllowedOrigins.validate(Arrays.asList("", "  ", null), false).isEmpty()).isTrue();
    }

    @Test
    void devProfile_acceptsPatternsAndJokerButStillValidatesExactOrigins() {
        CorsAllowedOrigins origins = CorsAllowedOrigins.validate(
                List.of("http://localhost:[*]", "*", "https://portail.example.test"), true);

        assertThat(origins.originPatterns()).containsExactly("http://localhost:[*]", "*");
        assertThat(origins.exactOrigins()).containsExactly("https://portail.example.test");

        refusal(true, "http://app.example.test").hasMessageContaining("loopback");
        refusal(true, "null").hasMessageContaining("opaque");
    }

    // La liste est construite hors du lambda : seule validate() peut y lever l'exception attendue.
    private static AbstractThrowableAssert<?, ? extends Throwable> refusal(boolean devProfile, String... values) {
        List<String> rawValues = List.of(values);
        return assertThatThrownBy(() -> CorsAllowedOrigins.validate(rawValues, devProfile));
    }
}
