package com.takibo.authorizationserver.infrastructure.springauthserver.token;

import com.takibo.authorizationserver.infrastructure.springauthserver.properties.TasAuthorizationServerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Assemble et signe la preuve humaine — et rien d'autre.
 * <p>
 * TAS ne vérifie pas les credentials, ne résout pas les codes, ne consulte aucune identité :
 * ces décisions appartiennent à TIS-CORE, qui appelle ce signeur via le port
 * {@code HumanAccessTokenIssuer} (adapter dans le boot). Le canon des claims reste ici,
 * à côté de {@link TakiboTokenClaims}, signé par la même clé que les tokens machine.
 * <p>
 * Fail-closed : un token humain sans org/space/account/user réels est une incohérence,
 * jamais un token partiellement situé.
 */
@Component
public class HumanTokenSigner {

    private final JwtEncoder jwtEncoder;
    private final TasAuthorizationServerProperties properties;
    private final Duration ttl;

    public HumanTokenSigner(JwtEncoder jwtEncoder,
                            TasAuthorizationServerProperties properties,
                            @Value("${takibo.tas.human-token.ttl-seconds:300}") long ttlSeconds) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public SignedHumanToken sign(HumanTokenCommand command) {
        if (command.orgId() == null || command.spaceId() == null
                || command.accountId() == null || command.userId() == null) {
            throw new IllegalStateException("HUMAN_TOKEN_REQUIRES_FULL_TENANT_IDENTITY");
        }

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(command.accountId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(TakiboTokenClaims.SUBJECT_TYPE, TakiboTokenClaims.SUBJECT_HUMAN)
                .claim(TakiboTokenClaims.AUTH_METHOD, TakiboTokenClaims.AUTH_PASSWORD)
                .claim(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_SPACE)
                .claim(TakiboTokenClaims.TENANT_SOURCE, TakiboTokenClaims.SOURCE_HUMAN_LOGIN)
                .claim(TakiboTokenClaims.ORG_ID, command.orgId().toString())
                .claim(TakiboTokenClaims.SPACE_ID, command.spaceId().toString())
                .claim(TakiboTokenClaims.ACCOUNT_ID, command.accountId().toString())
                .claim(TakiboTokenClaims.USER_ID, command.userId().toString())
                .claim(TakiboTokenClaims.ROLES, command.roles())
                .build();

        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new SignedHumanToken(tokenValue, ttl.toSeconds());
    }
}
