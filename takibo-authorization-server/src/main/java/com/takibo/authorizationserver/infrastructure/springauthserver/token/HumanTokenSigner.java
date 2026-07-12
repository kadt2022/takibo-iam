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
 * Deux formes, deux invariants fail-closed (IAM 31) :
 * <ul>
 *   <li><b>SPACE</b> — org, space, account et user réels, tous exigés ;</li>
 *   <li><b>ORGANIZATION</b> — org et account réels exigés, space et user absents par
 *       construction : l'organisation identifie le compte, le space situe l'action.</li>
 * </ul>
 * Jamais de token partiellement situé : une forme intermédiaire est une incohérence.
 */
@Component
public class HumanTokenSigner {

    private final JwtEncoder jwtEncoder;
    private final TasAuthorizationServerProperties properties;
    private final Duration spaceTtl;
    private final Duration organizationTtl;

    public HumanTokenSigner(JwtEncoder jwtEncoder,
                            TasAuthorizationServerProperties properties,
                            @Value("${takibo.tas.human-token.ttl-seconds:300}") long ttlSeconds,
                            @Value("${takibo.tas.human-org-token.ttl-seconds:"
                                    + "${takibo.tas.human-token.ttl-seconds:300}}") long orgTtlSeconds) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.spaceTtl = Duration.ofSeconds(ttlSeconds);
        this.organizationTtl = Duration.ofSeconds(orgTtlSeconds);
    }

    public SignedHumanToken sign(HumanTokenCommand command) {
        if (command.orgId() == null || command.accountId() == null) {
            throw new IllegalStateException("HUMAN_TOKEN_REQUIRES_ORG_AND_ACCOUNT");
        }
        boolean organizationScoped = command.spaceId() == null;
        if (organizationScoped && command.userId() != null) {
            throw new IllegalStateException("ORG_TOKEN_MUST_NOT_CARRY_USER");
        }
        if (!organizationScoped && command.userId() == null) {
            throw new IllegalStateException("HUMAN_TOKEN_REQUIRES_FULL_TENANT_IDENTITY");
        }

        return organizationScoped ? signOrganization(command) : signSpace(command);
    }

    private SignedHumanToken signSpace(HumanTokenCommand command) {
        Instant now = Instant.now();

        JwtClaimsSet claims = baseClaims(command, now, spaceTtl)
                .claim(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_SPACE)
                .claim(TakiboTokenClaims.SPACE_ID, command.spaceId().toString())
                .claim(TakiboTokenClaims.USER_ID, command.userId().toString())
                .build();

        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new SignedHumanToken(tokenValue, spaceTtl.toSeconds());
    }

    private SignedHumanToken signOrganization(HumanTokenCommand command) {
        Instant now = Instant.now();

        JwtClaimsSet claims = baseClaims(command, now, organizationTtl)
                .claim(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_ORGANIZATION)
                .build();

        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new SignedHumanToken(tokenValue, organizationTtl.toSeconds());
    }

    private JwtClaimsSet.Builder baseClaims(HumanTokenCommand command, Instant now, Duration ttl) {
        return JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(command.accountId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(TakiboTokenClaims.SUBJECT_TYPE, TakiboTokenClaims.SUBJECT_HUMAN)
                .claim(TakiboTokenClaims.AUTH_METHOD, TakiboTokenClaims.AUTH_PASSWORD)
                .claim(TakiboTokenClaims.TENANT_SOURCE, TakiboTokenClaims.SOURCE_HUMAN_LOGIN)
                .claim(TakiboTokenClaims.ORG_ID, command.orgId().toString())
                .claim(TakiboTokenClaims.ACCOUNT_ID, command.accountId().toString())
                .claim(TakiboTokenClaims.ROLES, command.roles())
                .claim(TakiboTokenClaims.GROUPS, command.groups())
                .claim(TakiboTokenClaims.PERMISSIONS, command.permissions());
    }
}
