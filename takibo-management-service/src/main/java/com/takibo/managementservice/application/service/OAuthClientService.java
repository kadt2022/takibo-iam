package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.RegisterClientCommand;
import com.takibo.managementservice.domain.exception.*;
import com.takibo.managementservice.domain.model.*;
import com.takibo.managementservice.domain.repository.OAuthClientRepository;
import com.takibo.managementservice.domain.vo.SpaceId;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class OAuthClientService {

  private static final int CLIENT_SECRET_LENGTH = 48;

  private final OAuthClientRepository repository;
  private final PasswordEncoder passwordEncoder;

  public RegisteredClientResult register(UUID orgId, SpaceId spaceId, RegisterClientCommand cmd) {
    validateInputs(orgId, spaceId, cmd);
    ensureClientIdIsFree(cmd.clientId());
    hardFailClientCredentialsPolicy(cmd);

    RegisterClientCommand normalized = normalizeForClientCredentials(cmd);

    TokenEndpointAuthMethod authMethod = resolveAuthMethod(normalized, normalized.clientType());
    enforcePublicBasics(normalized.clientType(), authMethod, normalized.requireClientSecret());
    enforceConfidentialBasics(normalized.clientType(), authMethod, normalized.requireClientSecret());

    ValidatedSets sets = validateAndNormalizeSets(normalized);
    boolean requirePkce = resolvePkce(normalized, normalized.clientType(), sets.grantTypes().contains("authorization_code"));
    enforceAuthorizationCodeRules(normalized.clientType(), sets, requirePkce);
    enforceClientCredentialsRules(normalized, authMethod, sets);
    enforcePublicSpaRules(normalized, authMethod, sets, requirePkce);
    enforceConfidentialAuthCodeRules(normalized, authMethod, sets);

    boolean requireSecret = resolveRequireSecret(normalized, normalized.clientType(), authMethod);
    Secrets secrets = generateSecretIfNeeded(requireSecret, normalized.clientSecretExpiresAt());

    OAuthClient client = buildDomain(orgId, spaceId, normalized, authMethod, requirePkce, sets, secrets);
    OAuthClient saved = repository.save(client);

    return new RegisteredClientResult(saved, secrets.plain());
  }

  private static void hardFailClientCredentialsPolicy(RegisterClientCommand cmd) {
    Set<String> normalizedGrantTypes = normalizeGrantTypesForPolicy(cmd.grantTypes());
    if (!normalizedGrantTypes.contains("client_credentials")) {
      return;
    }

    if (normalizedGrantTypes.size() > 1) {
      throw new InvalidClientConfigurationException("client_credentials cannot be combined with other grant types");
    }

    if (hasValues(cmd.redirectUris()) || hasValues(cmd.postLogoutRedirectUris()) || hasValues(cmd.corsOrigins())) {
      throw new InvalidClientConfigurationException("client_credentials must not include redirect/cors/post-logout URIs");
    }
  }

  private static Set<String> normalizeGrantTypesForPolicy(Set<String> grantTypes) {
    if (grantTypes == null || grantTypes.isEmpty()) {
      return Set.of();
    }
    return grantTypes.stream()
            .filter(v -> v != null)
            .map(v -> v.trim().toLowerCase())
            .collect(Collectors.toUnmodifiableSet());
  }

  private static boolean hasValues(Set<String> values) {
    return values != null && !values.isEmpty();
  }

  private static final String CLIENT_NOT_FOUND = "client not found";

  public RegisteredClientResult rotateSecret(UUID orgId, SpaceId spaceId, UUID clientId, Instant expiresAt) {
    Assert.notNull(orgId, "orgId is required");
    Assert.notNull(spaceId, "spaceId is required");
    Assert.notNull(clientId, "clientId is required");

    OAuthClient existing = repository.findByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId.value())
            .orElseThrow(() -> new InvalidClientConfigurationException(CLIENT_NOT_FOUND));
    if (existing.getClientType() == ClientType.PUBLIC || !usesSecret(existing.getTokenEndpointAuthMethod())) {
      throw new InvalidClientConfigurationException("client does not use secrets");
    }

    Instant newExpiresAt = expiresAt != null ? expiresAt : existing.getClientSecretExpiresAt();
    Secrets secrets = generateSecretIfNeeded(true, newExpiresAt);
    boolean updated = repository.updateSecretByIdAndOrgIdAndSpaceId(
            clientId, orgId, spaceId.value(), secrets.hash(), secrets.expiresAt());
    if (!updated) {
      throw new InvalidClientConfigurationException(CLIENT_NOT_FOUND);
    }

    return new RegisteredClientResult(existing.withSecret(secrets.hash(), secrets.expiresAt()), secrets.plain());
  }

  private static void validateInputs(UUID orgId, SpaceId spaceId, RegisterClientCommand cmd) {
    Assert.notNull(orgId, "orgId is required");
    Assert.notNull(spaceId, "spaceId is required");
    Assert.notNull(cmd, "command is required");
    Assert.hasText(cmd.clientId(), "clientId is required");
    Assert.hasText(cmd.clientName(), "clientName is required");
    Assert.notNull(cmd.clientType(), "clientType is required");
  }

  private void ensureClientIdIsFree(String clientId) {
    if (repository.existsByClientId(clientId)) {
      throw new ClientAlreadyExistsException(clientId);
    }
  }

  private static TokenEndpointAuthMethod resolveAuthMethod(RegisterClientCommand cmd, ClientType type) {
    TokenEndpointAuthMethod explicit = cmd.tokenEndpointAuthMethod();
    if (explicit != null) return explicit;
    if (type == ClientType.PUBLIC) return TokenEndpointAuthMethod.none;
    return TokenEndpointAuthMethod.client_secret_basic;
  }

  private static void enforcePublicBasics(ClientType type, TokenEndpointAuthMethod method, Boolean requireClientSecretFlag) {
    if (type == ClientType.PUBLIC && method != TokenEndpointAuthMethod.none) {
      throw new PublicClientAuthMethodNotNoneException(method.name());
    }
    if (type == ClientType.PUBLIC && Boolean.TRUE.equals(requireClientSecretFlag)) {
      throw new PublicClientMustNotHaveSecretException();
    }
  }

  private static void enforceConfidentialBasics(ClientType type, TokenEndpointAuthMethod method, Boolean requireClientSecretFlag) {
    if (type != ClientType.CONFIDENTIAL) {
      return;
    }
    if (method == TokenEndpointAuthMethod.none) {
      throw new InvalidClientConfigurationException("Confidential clients require clientSecret (token_endpoint_auth_method cannot be none)");
    }
    if (Boolean.FALSE.equals(requireClientSecretFlag)) {
      throw new InvalidClientConfigurationException("Confidential clients require clientSecret (requireClientSecret=true)");
    }
  }

  private static void enforceClientCredentialsRules(RegisterClientCommand cmd,
                                                    TokenEndpointAuthMethod authMethod,
                                                    ValidatedSets sets) {
    if (!sets.grantTypes().contains("client_credentials")) {
      return;
    }
    if (sets.grantTypes().size() != 1) {
      throw new InvalidClientConfigurationException("client_credentials cannot be combined with other grant types");
    }
    if (cmd.clientType() != ClientType.CONFIDENTIAL) {
      throw new InvalidClientConfigurationException("client_credentials requires clientType=CONFIDENTIAL");
    }
    if (authMethod == TokenEndpointAuthMethod.none) {
      throw new InvalidClientConfigurationException("client_credentials requires client authentication");
    }
    if (Boolean.TRUE.equals(cmd.requirePkce())) {
      throw new InvalidClientConfigurationException("client_credentials must not use PKCE");
    }
    if (!sets.redirectUris().isEmpty() || !sets.postLogoutRedirectUris().isEmpty() || !sets.corsOrigins().isEmpty()) {
      throw new InvalidClientConfigurationException("client_credentials must not include redirect/cors/post-logout URIs");
    }
  }

  private static void enforcePublicSpaRules(RegisterClientCommand cmd,
                                            TokenEndpointAuthMethod authMethod,
                                            ValidatedSets sets,
                                            boolean requirePkce) {
    if (cmd.clientType() != ClientType.PUBLIC) {
      return;
    }
    if (authMethod != TokenEndpointAuthMethod.none) {
      throw new InvalidClientConfigurationException("PUBLIC clients must use token_endpoint_auth_method=none");
    }
    if (Boolean.TRUE.equals(cmd.requireClientSecret())) {
      throw new InvalidClientConfigurationException("PUBLIC clients must not require clientSecret");
    }
    if (sets.grantTypes().contains("authorization_code")) {
      if (!requirePkce) {
        throw new InvalidClientConfigurationException("PUBLIC authorization_code requires PKCE");
      }
      if (sets.redirectUris().isEmpty()) {
        throw new InvalidClientConfigurationException("authorization_code requires redirectUris");
      }
      if (sets.corsOrigins().isEmpty()) {
        throw new InvalidClientConfigurationException("PUBLIC clients should declare corsOrigins");
      }
    }
  }

  private static void enforceConfidentialAuthCodeRules(RegisterClientCommand cmd,
                                                       TokenEndpointAuthMethod authMethod,
                                                       ValidatedSets sets) {
    if (cmd.clientType() != ClientType.CONFIDENTIAL || !sets.grantTypes().contains("authorization_code")) {
      return;
    }
    if (authMethod == TokenEndpointAuthMethod.none) {
      throw new InvalidClientConfigurationException("CONFIDENTIAL authorization_code requires client authentication");
    }
    if (Boolean.FALSE.equals(cmd.requireClientSecret())) {
      throw new InvalidClientConfigurationException("CONFIDENTIAL clients require clientSecret");
    }
    if (sets.redirectUris().isEmpty()) {
      throw new InvalidClientConfigurationException("authorization_code requires redirectUris");
    }
  }

  private static ValidatedSets validateAndNormalizeSets(RegisterClientCommand cmd) {
    Set<String> grantTypes = ClientGrantType.ofAll(cmd.grantTypes())
            .stream().map(ClientGrantType::getValue).collect(Collectors.toUnmodifiableSet());

    Set<String> scopes = ClientScope.ofAll(cmd.scopes())
            .stream().map(ClientScope::getValue).collect(Collectors.toUnmodifiableSet());

    Set<String> redirectUris = ClientRedirectUri.ofAll(cmd.redirectUris())
            .stream().map(ClientRedirectUri::getUri).collect(Collectors.toUnmodifiableSet());

    Set<String> postLogoutRedirectUris = ClientPostLogoutRedirectUri.ofAll(cmd.postLogoutRedirectUris())
            .stream().map(ClientPostLogoutRedirectUri::getUri).collect(Collectors.toUnmodifiableSet());

    Set<String> corsOrigins = ClientCorsOrigin.ofAll(cmd.corsOrigins())
            .stream().map(ClientCorsOrigin::getOrigin).collect(Collectors.toUnmodifiableSet());

    return new ValidatedSets(grantTypes, scopes, redirectUris, postLogoutRedirectUris, corsOrigins);
  }

  private static boolean resolvePkce(RegisterClientCommand cmd, ClientType type, boolean hasAuthorizationCode) {
    Boolean explicit = cmd.requirePkce();
    if (explicit != null) return explicit;
    return type == ClientType.PUBLIC && hasAuthorizationCode;
  }

  private static void enforceAuthorizationCodeRules(ClientType type, ValidatedSets sets, boolean requirePkce) {
    boolean hasAuthorizationCode = sets.grantTypes().contains("authorization_code");
    if (!hasAuthorizationCode) return;

    if (sets.redirectUris().isEmpty()) {
      throw new AuthorizationCodeRequiresRedirectUriException();
    }
    if (type == ClientType.PUBLIC && !requirePkce) {
      throw new PublicAuthorizationCodeRequiresPkceException();
    }
  }

  private static boolean resolveRequireSecret(RegisterClientCommand cmd, ClientType type, TokenEndpointAuthMethod method) {
    if (type == ClientType.PUBLIC) return false;
    boolean requested = Boolean.TRUE.equals(cmd.requireClientSecret());
    return requested || usesSecret(method);
  }

  private static boolean usesSecret(TokenEndpointAuthMethod method) {
    return method == TokenEndpointAuthMethod.client_secret_basic
            || method == TokenEndpointAuthMethod.client_secret_post
            || method == TokenEndpointAuthMethod.client_secret_jwt;
  }

  private static RegisterClientCommand normalizeForClientCredentials(RegisterClientCommand cmd) {
    if (cmd.grantTypes() == null || !cmd.grantTypes().contains("client_credentials")) {
      return cmd;
    }
    TokenEndpointAuthMethod normalizedAuthMethod = cmd.tokenEndpointAuthMethod();
    if (normalizedAuthMethod == null || normalizedAuthMethod == TokenEndpointAuthMethod.none) {
      normalizedAuthMethod = TokenEndpointAuthMethod.client_secret_basic;
    }

    return new RegisterClientCommand(
            cmd.clientId(),
            cmd.clientName(),
            ClientType.CONFIDENTIAL,
            true,
            normalizedAuthMethod,
            false,
            false,
            cmd.jwksUri(),
            cmd.jwksJson(),
            cmd.idTokenSignedAlg(),
            cmd.accessTokenTtlSeconds(),
            cmd.refreshTokenTtlSeconds(),
            cmd.idTokenTtlSeconds(),
            cmd.clientSecretExpiresAt(),
            cmd.scopes(),
            cmd.grantTypes(),
            Set.of(),
            Set.of(),
            Set.of()
    );
  }

  private Secrets generateSecretIfNeeded(boolean requireSecret, Instant expiresAt) {
    if (!requireSecret) return Secrets.none();
    String plain = SecretGeneratorUtil.randomUrlSafe(CLIENT_SECRET_LENGTH);
    String hash  = passwordEncoder.encode(plain);
    return new Secrets(plain, hash, expiresAt);
  }

  private static OAuthClient buildDomain(
          UUID orgId,
          SpaceId spaceId,
          RegisterClientCommand cmd,
          TokenEndpointAuthMethod authMethod,
          boolean requirePkce,
          ValidatedSets sets,
          Secrets secrets
  ) {

    OAuthClient client = OAuthClient.create(orgId, spaceId, cmd.clientId(), cmd.clientName(), cmd.clientType())
            .toBuilder()
            .tokenEndpointAuthMethod(authMethod)
            .requirePkce(requirePkce)
            .requireConsent(Boolean.TRUE.equals(cmd.requireConsent()))
            .jwksUri(cmd.jwksUri())
            .jwksJson(cmd.jwksJson())
            .idTokenSignedAlg(cmd.idTokenSignedAlg())
            .accessTokenTtlSeconds(cmd.accessTokenTtlSeconds())
            .refreshTokenTtlSeconds(cmd.refreshTokenTtlSeconds())
            .idTokenTtlSeconds(cmd.idTokenTtlSeconds())
            .scopes(sets.scopes())
            .grantTypes(sets.grantTypes())
            .redirectUris(sets.redirectUris())
            .postLogoutRedirectUris(sets.postLogoutRedirectUris())
            .corsOrigins(sets.corsOrigins())
            .additionalSettings(java.util.Map.of())
            .build();

    if (secrets.hash() != null) {
      client = client.withSecret(secrets.hash(), secrets.expiresAt());
    }
    return client;
  }
}
