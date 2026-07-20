package com.takibo.managementservice.interfaces.rest.request;

import com.takibo.audit.annotations.Sensitive;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public record ClientRegistrationRequest(
        @NotBlank
        @Size(max = 128)
        @Pattern(
                regexp = "[A-Za-z0-9][A-Za-z0-9._~-]*",
                message = "clientId contains unsupported characters"
        )
        String clientId,

        @NotBlank
        @Size(max = 160)
        String clientName,

        @NotNull ClientType clientType,
        Boolean requireClientSecret,
        TokenEndpointAuthMethod tokenEndpointAuthMethod,
        Boolean requirePkce,
        Boolean requireConsent,

        @Size(max = 255)
        String jwksUri,

        @Sensitive
        @Size(max = 32768)
        String jwksJson,

        @Size(max = 32)
        String idTokenSignedAlg,

        @Positive
        @Max(86_400)
        Integer accessTokenTtlSeconds,

        @Positive
        @Max(31_536_000)
        Integer refreshTokenTtlSeconds,

        @Positive
        @Max(86_400)
        Integer idTokenTtlSeconds,

        @Future
        Instant clientSecretExpiresAt,

        @Size(max = 50)
        Set<@NotBlank @Size(max = 128) String> scopes,

        @Size(max = 10)
        Set<@NotBlank @Size(max = 64) String> grantTypes,

        @Size(max = 20)
        Set<@NotBlank @Size(max = 255) String> redirectUris,

        @Size(max = 20)
        Set<@NotBlank @Size(max = 255) String> postLogoutRedirectUris,

        @Size(max = 20)
        Set<@NotBlank @Size(max = 255) String> corsOrigins
) {
    @Override
    public String toString() {
        return "ClientRegistrationRequest[clientId=" + clientId
                + ", clientName=" + clientName
                + ", clientType=" + clientType
                + ", jwksJson=" + (jwksJson == null ? null : "********")
                + "]";
    }
}
