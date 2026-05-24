package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.AccountId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true)
public class Account {
    @EqualsAndHashCode.Include
    private final AccountId id;
    private UUID orgId;
    private final EmailAddress email;
    private final String displayName;
    private final String avatarUrl;
    private final Map<String, Object> metadata;
    private final Instant createdAt;
    private final Instant updatedAt;
    @Builder.Default
    private final Long version = 0L;

    public static Account create(EmailAddress email, String displayName, String avatarUrl, Map<String, Object> metadata) {
        Instant now = Instant.now();
        return Account.builder()
                .id(AccountId.newId())
                .email(email)
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .metadata(metadata)
                .createdAt(now)
                .updatedAt(now)
                .version(0L)
                .build();
    }

    public Account withOrgId(UUID orgId) {
        return this.toBuilder().orgId(orgId).build();
    }

}
