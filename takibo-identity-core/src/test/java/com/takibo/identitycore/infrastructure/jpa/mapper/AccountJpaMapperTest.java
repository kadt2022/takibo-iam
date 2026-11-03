package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.infrastructure.entity.AccountEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountJpaMapperTest {

    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    private final AccountJpaMapper mapper = Mappers.getMapper(AccountJpaMapper.class);

    @Test
    void toEntity_storesTheCanonicalEmailOfTheOrganizationAccount() {
        Account account = Account.builder()
                .id(AccountId.of(ACCOUNT_UUID)).orgId(ORG_UUID)
                .email(new EmailAddress(" Jane.Doe@Example.COM "))
                .displayName("Jane").avatarUrl("https://cdn/x.png")
                .metadata(Map.of("locale", "fr"))
                .version(3L)
                .build();

        AccountEntity entity = mapper.toEntity(account);

        assertThat(entity.getId()).isEqualTo(ACCOUNT_UUID);
        assertThat(entity.getOrgId()).isEqualTo(ORG_UUID);
        assertThat(entity.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(entity.getDisplayName()).isEqualTo("Jane");
        assertThat(entity.getAvatarUrl()).isEqualTo("https://cdn/x.png");
        assertThat(entity.getMetadata()).containsExactlyEntriesOf(Map.of("locale", "fr"));
        assertThat(entity.getVersion()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.isNew()).isTrue();
    }

    @Test
    void toDomain_revalidatesTheStoredEmail() {
        AccountEntity entity = mapper.toEntity(Account.builder()
                .id(AccountId.of(ACCOUNT_UUID)).orgId(ORG_UUID)
                .email(new EmailAddress("jane@example.com")).build());
        entity.setCreatedAt(CREATED);
        entity.setVersion(2L);

        Account account = mapper.toDomain(entity);

        assertThat(account.getId()).isEqualTo(AccountId.of(ACCOUNT_UUID));
        assertThat(account.getOrgId()).isEqualTo(ORG_UUID);
        assertThat(account.getEmail()).isEqualTo(new EmailAddress("jane@example.com"));
        assertThat(account.getCreatedAt()).isEqualTo(CREATED);
        assertThat(account.getVersion()).isEqualTo(2L);

        entity.setEmail("not-an-email");
        assertThatThrownBy(() -> mapper.toDomain(entity)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullsAreTolerated() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
        Account orphan = mapper.toDomain(new AccountEntity());
        assertThat(orphan.getId()).isNull();
        assertThat(orphan.getEmail()).isNull();
    }
}
