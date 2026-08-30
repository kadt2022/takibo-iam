package com.takibo.authorizationserver.infrastructure.springauthserver.authorization;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2AuthorizationConsentEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2AuthorizationConsentRepository;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.TakiboTokenClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link OAuth2AuthorizationConsentService} persistant (TAS-GRANTS-02).
 * <p>
 * Clé de lecture globale sur {@code (registered_client_id, principal_name)} — voir
 * V202608290001 : {@link #findById} n'a aucun paramètre de tenant, exactement comme sa
 * signature le montre. {@code principal_account_id} reste NULL à l'écriture : ce service ne
 * reçoit jamais d'identifiant de compte de Spring Authorization Server, seulement un
 * {@code principalName} — voir V202608290003.
 */
@Service
@RequiredArgsConstructor
public class JpaOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final OAuth2AuthorizationConsentRepository consents;
    private final RegisteredClientRepository registeredClientRepository;

    @Override
    @Transactional
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent cannot be null");
        RegisteredClient client = requireResolvableClient(authorizationConsent.getRegisteredClientId());

        OAuth2AuthorizationConsentEntity existing = consents
                .findByRegisteredClientIdAndPrincipalName(
                        authorizationConsent.getRegisteredClientId(),
                        authorizationConsent.getPrincipalName())
                .orElse(null);

        OAuth2AuthorizationConsentEntity.OAuth2AuthorizationConsentEntityBuilder entity =
                OAuth2AuthorizationConsentEntity.builder()
                        .id(existing != null ? existing.getId() : UUID.randomUUID())
                        .orgId(readUuidSetting(client, TakiboTokenClaims.ORG_ID))
                        .spaceId(readUuidSetting(client, TakiboTokenClaims.SPACE_ID))
                        .registeredClientId(authorizationConsent.getRegisteredClientId())
                        .principalAccountId(null)
                        .subjectType("HUMAN")
                        .principalName(authorizationConsent.getPrincipalName())
                        .authorities(joinAuthorities(authorizationConsent.getAuthorities()));

        consents.save(entity.build());
    }

    @Override
    @Transactional
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent cannot be null");
        consents.findByRegisteredClientIdAndPrincipalName(
                        authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName())
                .ifPresent(entity -> consents.deleteById(entity.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        Assert.hasText(registeredClientId, "registeredClientId cannot be empty");
        Assert.hasText(principalName, "principalName cannot be empty");
        return consents.findByRegisteredClientIdAndPrincipalName(registeredClientId, principalName)
                .map(this::toDomain)
                .orElse(null);
    }

    private OAuth2AuthorizationConsent toDomain(OAuth2AuthorizationConsentEntity entity) {
        // La resolvabilite du client n'est pas necessaire pour reconstruire l'objet — voir
        // OAuth2AuthorizationConsent.withId, qui ne prend qu'un identifiant, jamais un
        // RegisteredClient — mais elle l'est pour rester coherent avec la doctrine du récit :
        // un consentement pour un client devenu inconnu ne devrait pas se relire en silence.
        requireResolvableClient(entity.getRegisteredClientId());

        OAuth2AuthorizationConsent.Builder builder =
                OAuth2AuthorizationConsent.withId(entity.getRegisteredClientId(), entity.getPrincipalName());
        splitAuthorities(entity.getAuthorities()).forEach(builder::authority);
        return builder.build();
    }

    private RegisteredClient requireResolvableClient(String registeredClientId) {
        RegisteredClient client = registeredClientRepository.findById(registeredClientId);
        if (client == null) {
            throw new DataRetrievalFailureException(
                    "The RegisteredClient with id '" + registeredClientId + "' was not found");
        }
        return client;
    }

    private static UUID readUuidSetting(RegisteredClient client, String settingName) {
        String value = client.getClientSettings().getSetting(settingName);
        return StringUtils.hasText(value) ? UUID.fromString(value) : null;
    }

    private static String joinAuthorities(Set<GrantedAuthority> authorities) {
        if (CollectionUtils.isEmpty(authorities)) {
            return "";
        }
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
    }

    private static Set<GrantedAuthority> splitAuthorities(String commaDelimited) {
        if (!StringUtils.hasText(commaDelimited)) {
            return Set.of();
        }
        return StringUtils.commaDelimitedListToSet(commaDelimited).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }
}
