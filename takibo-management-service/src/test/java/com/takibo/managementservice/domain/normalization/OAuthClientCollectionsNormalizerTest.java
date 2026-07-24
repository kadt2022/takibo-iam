package com.takibo.managementservice.domain.normalization;

import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthClientCollectionsNormalizerTest {

    private final OAuthClientCollectionsNormalizer normalizer =
            new OAuthClientCollectionsNormalizer();

    @Test
    void normalizes_each_oauth_collection_through_its_value_objects() {
        var normalizedCollections = normalizer.normalizeCollections(
                new OAuthClientRegistration(
                        "client-test",
                        "Client Test",
                        ClientType.PUBLIC,
                        false,
                        TokenEndpointAuthMethod.none,
                        true,
                        false,
                        null,
                        null,
                        "RS256",
                        900,
                        3600,
                        900,
                        null,
                        Set.of(" api:read "),
                        Set.of(" AUTHORIZATION_CODE "),
                        Set.of(" https://app.example/callback "),
                        Set.of(" https://app.example/logout "),
                        Set.of(" https://app.example ")
                )
        );

        assertThat(normalizedCollections.scopes())
                .containsExactly("api:read");
        assertThat(normalizedCollections.grantTypes())
                .containsExactly("authorization_code");
        assertThat(normalizedCollections.redirectUris())
                .containsExactly("https://app.example/callback");
        assertThat(normalizedCollections.postLogoutRedirectUris())
                .containsExactly("https://app.example/logout");
        assertThat(normalizedCollections.corsOrigins())
                .containsExactly("https://app.example");
    }
}
