package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.AccountIdentityId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true)
public class AccountIdentity {

    @EqualsAndHashCode.Include
    private final AccountIdentityId id;

    private final AccountId accountId;

    // Unicité (provider, issuer, subject)
    private final String provider;
    private final String issuer;
    private final String subject;

    private final String name;
    private final String email;
    private final String avatarUrl;

    private final Map<String, Object> rawClaims;

    private final Instant createdAt;
    private final Instant updatedAt;
    @Builder.Default
    private final Long version = 0L;

    public static AccountIdentity create(AccountId accountId, String provider, String issuer, String subject,
                                         String name, String email, String avatarUrl, Map<String, Object> rawClaims) {
        Instant now = Instant.now();
        return AccountIdentity.builder()
                .id(AccountIdentityId.newId())
                .accountId(accountId)
                .provider(provider)
                .issuer(issuer)
                .subject(subject)
                .name(name)
                .email(email)
                .avatarUrl(avatarUrl)
                .rawClaims(rawClaims)
                .createdAt(now)
                .updatedAt(now)
                .version(0L)
                .build();
    }
}
