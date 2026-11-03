package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.PasswordHash;
import com.takibo.identitycore.infrastructure.entity.AccountCredentialsEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les credentials franchissent la frontière JPA sans perdre ni le hash (et son
 * algorithme) ni l'état de verrouillage — sinon un compte verrouillé ressortirait
 * déverrouillé ou un hash serait relu avec le mauvais algorithme.
 */
class AccountCredentialsJpaMapperTest {

    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final Instant PASSWORD_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LOCKED_UNTIL = Instant.parse("2026-01-01T00:15:00Z");

    private final AccountCredentialsJpaMapper mapper = Mappers.getMapper(AccountCredentialsJpaMapper.class);

    private static AccountCredentials lockedCredentials() {
        return AccountCredentials.builder()
                .accountId(AccountId.of(ACCOUNT_UUID))
                .passwordHash(PasswordHash.of("$argon2id$hash", "argon2id", 2))
                .passwordUpdatedAt(PASSWORD_AT)
                .mustChangeNextLogin(true)
                .failedAttempts(5)
                .lockedUntil(LOCKED_UNTIL)
                .version(4L)
                .build();
    }

    @Test
    void toEntity_situatesCredentialsInTheOrganization_andSplitsTheHash() {
        AccountCredentialsEntity entity = mapper.toEntity(lockedCredentials(), ORG_UUID);

        assertThat(entity.getOrgId()).isEqualTo(ORG_UUID);
        assertThat(entity.getAccountId()).isEqualTo(ACCOUNT_UUID);
        assertThat(entity.getPasswordHash()).isEqualTo("$argon2id$hash");
        assertThat(entity.getPasswordAlgo()).isEqualTo("argon2id");
        assertThat(entity.getPasswordVersion()).isEqualTo(2);
        assertThat(entity.getPasswordUpdatedAt()).isEqualTo(PASSWORD_AT);
        assertThat(entity.isMustChangeNextLogin()).isTrue();
        assertThat(entity.getFailedAttempts()).isEqualTo(5);
        assertThat(entity.getLockedUntil()).isEqualTo(LOCKED_UNTIL);
    }

    @Test
    void toEntity_isAlwaysANewRow_whoseVersionAndTimestampsBelongToPersistence() {
        AccountCredentialsEntity entity = mapper.toEntity(lockedCredentials(), ORG_UUID);

        assertThat(entity.isNew()).isTrue();
        assertThat(entity.getVersion()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
        assertThat(entity.getAccount()).isNull();
    }

    @Test
    void toEntity_withoutHash_leavesHashColumnsEmpty() {
        AccountCredentials noHash = AccountCredentials.builder().accountId(AccountId.of(ACCOUNT_UUID)).build();

        AccountCredentialsEntity entity = mapper.toEntity(noHash, ORG_UUID);

        assertThat(entity.getPasswordHash()).isNull();
        assertThat(entity.getPasswordAlgo()).isNull();
        assertThat(entity.getPasswordVersion()).isNull();
    }

    @Test
    void toEntity_withoutAnySource_yieldsNull() {
        assertThat(mapper.toEntity(null, null)).isNull();
    }

    @Test
    void roundTrip_keepsTheLockAndTheHashAlgorithm() {
        AccountCredentialsEntity entity = mapper.toEntity(lockedCredentials(), ORG_UUID);

        AccountCredentials back = mapper.toDomain(entity);

        assertThat(back.getAccountId()).isEqualTo(AccountId.of(ACCOUNT_UUID));
        assertThat(back.getPasswordHash().getHash()).isEqualTo("$argon2id$hash");
        assertThat(back.getPasswordHash().getAlgo()).isEqualTo("argon2id");
        assertThat(back.getPasswordHash().getVersion()).isEqualTo(2);
        assertThat(back.getFailedAttempts()).isEqualTo(5);
        assertThat(back.getLockedUntil()).isEqualTo(LOCKED_UNTIL);
        assertThat(back.isMustChangeNextLogin()).isTrue();
        assertThat(back.getAccount()).isNull();
    }

    @Test
    void toDomain_legacyRowWithoutAlgorithm_defaultsToBcrypt() {
        AccountCredentialsEntity entity = mapper.toEntity(lockedCredentials(), ORG_UUID);
        entity.setPasswordAlgo(null);

        assertThat(mapper.toDomain(entity).getPasswordHash().getAlgo()).isEqualTo("bcrypt");
    }

    @Test
    void toDomain_withoutHashOrSource_yieldsNoHash() {
        AccountCredentialsEntity entity = mapper.toEntity(
                AccountCredentials.builder().accountId(AccountId.of(ACCOUNT_UUID)).build(), ORG_UUID);

        assertThat(mapper.toDomain(entity).getPasswordHash()).isNull();
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toPasswordHash(null)).isNull();
    }
}
