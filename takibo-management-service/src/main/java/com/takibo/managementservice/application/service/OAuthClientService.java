package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.RegisterClientCommand;
import com.takibo.managementservice.domain.exception.ClientAlreadyExistsException;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.exception.OAuthClientSecretRotationConflictException;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.OAuthClientRegistrationPlan;
import com.takibo.managementservice.domain.model.RegisteredClientResult;
import com.takibo.managementservice.domain.model.Secrets;
import com.takibo.managementservice.domain.repository.OAuthClientRepository;
import com.takibo.managementservice.domain.service.OAuthClientRegistrationDomainService;
import com.takibo.managementservice.domain.service.OAuthClientSecretRotationDomainService;
import com.takibo.managementservice.domain.vo.SpaceId;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OAuthClientService {

    private static final int CLIENT_SECRET_LENGTH = 48;
    private static final String CLIENT_NOT_FOUND = "client not found";

    private final OAuthClientRepository oauthClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final OAuthClientRegistrationDomainService
            oauthClientRegistrationDomainService;
    private final OAuthClientSecretRotationDomainService
            oauthClientSecretRotationDomainService;

    public RegisteredClientResult register(
            UUID orgId,
            SpaceId spaceId,
            RegisterClientCommand command
    ) {
        validateInputs(orgId, spaceId, command);

        OAuthClientRegistrationPlan plan =
                oauthClientRegistrationDomainService.prepareRegistration(
                        toDomainRegistration(command)
                );

        ensureClientIdIsFree(plan.registration().clientId());

        Secrets secrets = generateSecretIfNeeded(
                plan.requireSecret(),
                plan.registration().clientSecretExpiresAt()
        );
        OAuthClient client = plan.createClient(orgId, spaceId, secrets);
        OAuthClient saved = oauthClientRepository.save(client);

        return new RegisteredClientResult(saved, secrets.plain());
    }

    public RegisteredClientResult rotateSecret(
            UUID orgId,
            SpaceId spaceId,
            UUID clientId,
            Instant expiresAt
    ) {
        Assert.notNull(orgId, "orgId is required");
        Assert.notNull(spaceId, "spaceId is required");
        Assert.notNull(clientId, "clientId is required");
        oauthClientSecretRotationDomainService
                .validateRequestedExpiration(expiresAt);

        OAuthClient existing = oauthClientRepository
                .findByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId.value())
                .orElseThrow(() ->
                        new InvalidClientConfigurationException(CLIENT_NOT_FOUND)
                );

        Instant newExpiresAt = oauthClientSecretRotationDomainService
                .resolveExpiration(existing, expiresAt);
        Secrets secrets = generateSecretIfNeeded(true, newExpiresAt);

        boolean updated =
                oauthClientRepository.updateSecretByIdAndOrgIdAndSpaceId(
                clientId,
                orgId,
                spaceId.value(),
                existing.getVersion(),
                secrets.hash(),
                secrets.expiresAt()
        );
        if (!updated) {
            throw new OAuthClientSecretRotationConflictException();
        }

        return new RegisteredClientResult(
                existing.withSecret(secrets.hash(), secrets.expiresAt()),
                secrets.plain()
        );
    }

    private static void validateInputs(
            UUID orgId,
            SpaceId spaceId,
            RegisterClientCommand command
    ) {
        Assert.notNull(orgId, "orgId is required");
        Assert.notNull(spaceId, "spaceId is required");
        Assert.notNull(command, "command is required");
        Assert.hasText(command.clientId(), "clientId is required");
        Assert.hasText(command.clientName(), "clientName is required");
        Assert.notNull(command.clientType(), "clientType is required");
    }

    private void ensureClientIdIsFree(String clientId) {
        if (oauthClientRepository.existsByClientId(clientId)) {
            throw new ClientAlreadyExistsException(clientId);
        }
    }

    private Secrets generateSecretIfNeeded(
            boolean requireSecret,
            Instant expiresAt
    ) {
        if (!requireSecret) {
            return Secrets.none();
        }
        String plain = SecretGeneratorUtil.randomUrlSafe(CLIENT_SECRET_LENGTH);
        String hash = passwordEncoder.encode(plain);
        return new Secrets(plain, hash, expiresAt);
    }

    private static OAuthClientRegistration toDomainRegistration(
            RegisterClientCommand command
    ) {
        return new OAuthClientRegistration(
                command.clientId(),
                command.clientName(),
                command.clientType(),
                command.requireClientSecret(),
                command.tokenEndpointAuthMethod(),
                command.requirePkce(),
                command.requireConsent(),
                command.jwksUri(),
                command.jwksJson(),
                command.idTokenSignedAlg(),
                command.accessTokenTtlSeconds(),
                command.refreshTokenTtlSeconds(),
                command.idTokenTtlSeconds(),
                command.clientSecretExpiresAt(),
                command.scopes(),
                command.grantTypes(),
                command.redirectUris(),
                command.postLogoutRedirectUris(),
                command.corsOrigins()
        );
    }
}
