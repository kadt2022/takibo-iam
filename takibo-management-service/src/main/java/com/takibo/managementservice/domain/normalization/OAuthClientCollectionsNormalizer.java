package com.takibo.managementservice.domain.normalization;

import com.takibo.managementservice.domain.model.ClientCorsOrigin;
import com.takibo.managementservice.domain.model.ClientGrantType;
import com.takibo.managementservice.domain.model.ClientPostLogoutRedirectUri;
import com.takibo.managementservice.domain.model.ClientRedirectUri;
import com.takibo.managementservice.domain.model.ClientScope;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.ValidatedSets;

import java.util.Set;
import java.util.stream.Collectors;

public final class OAuthClientCollectionsNormalizer {

    public ValidatedSets normalizeCollections(
            OAuthClientRegistration registration
    ) {
        Set<String> grantTypes = ClientGrantType.ofAll(registration.grantTypes())
                .stream()
                .map(ClientGrantType::getValue)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> scopes = ClientScope.ofAll(registration.scopes())
                .stream()
                .map(ClientScope::getValue)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> redirectUris =
                ClientRedirectUri.ofAll(registration.redirectUris())
                        .stream()
                        .map(ClientRedirectUri::getUri)
                        .collect(Collectors.toUnmodifiableSet());

        Set<String> postLogoutRedirectUris =
                ClientPostLogoutRedirectUri.ofAll(
                                registration.postLogoutRedirectUris()
                        )
                        .stream()
                        .map(ClientPostLogoutRedirectUri::getUri)
                        .collect(Collectors.toUnmodifiableSet());

        Set<String> corsOrigins =
                ClientCorsOrigin.ofAll(registration.corsOrigins())
                        .stream()
                        .map(ClientCorsOrigin::getOrigin)
                        .collect(Collectors.toUnmodifiableSet());

        return new ValidatedSets(
                grantTypes,
                scopes,
                redirectUris,
                postLogoutRedirectUris,
                corsOrigins
        );
    }
}
