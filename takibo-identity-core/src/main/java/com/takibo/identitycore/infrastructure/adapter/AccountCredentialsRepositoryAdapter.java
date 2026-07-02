package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.infrastructure.entity.AccountCredentialsEntity;
import com.takibo.identitycore.infrastructure.entity.AccountEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.AccountCredentialsJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaAccountCredentialsRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;


@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountCredentialsRepositoryAdapter implements AccountCredentialsRepository {

    private final JpaAccountCredentialsRepository jpa;
    private final AccountCredentialsJpaMapper mapper;
    private final EntityManager em;

    @Override
    @Transactional
    public AccountCredentials save(AccountCredentials domain, UUID orgId) {
        UUID accountId = domain.getAccountId().getValue();
        AccountCredentialsEntity.AccountCredentialsId id =
                new AccountCredentialsEntity.AccountCredentialsId(orgId, accountId);

        // Ligne existante (échec/reset de login, rotation de mot de passe) : on mute
        // l'entité CHARGÉE. mapper.toEntity() marque isNew=true (Persistable) et
        // provoquerait un INSERT en doublon de la PK (org_id, account_id).
        Optional<AccountCredentialsEntity> existing = jpa.findById(id);
        if (existing.isPresent()) {
            AccountCredentialsEntity entity = applyMutableState(existing.get(), domain);
            return mapper.toDomain(jpa.saveAndFlush(entity));
        }

        AccountCredentialsEntity entity = mapper.toEntity(domain, orgId);
        AccountEntity accountRef = em.getReference(AccountEntity.class, accountId);

        entity.setAccount(accountRef);
        entity.setOrgId(orgId);
        entity.setAccountId(accountId);

        return mapper.toDomain(jpa.saveAndFlush(entity));
    }

    private AccountCredentialsEntity applyMutableState(AccountCredentialsEntity entity, AccountCredentials domain) {
        entity.setPasswordHash(domain.getPasswordHash().getHash());
        entity.setPasswordAlgo(domain.getPasswordHash().getAlgo());
        entity.setPasswordVersion(domain.getPasswordHash().getVersion());
        entity.setPasswordUpdatedAt(domain.getPasswordUpdatedAt());
        entity.setMustChangeNextLogin(domain.isMustChangeNextLogin());
        entity.setFailedAttempts(domain.getFailedAttempts());
        entity.setLockedUntil(domain.getLockedUntil());
        return entity;
    }

    @Override
    public Optional<AccountCredentials> find(OrganizationId orgId, AccountId accountId) {
        AccountCredentialsEntity.AccountCredentialsId id =
                new AccountCredentialsEntity.AccountCredentialsId(orgId.getValue(), accountId.getValue());
        return jpa.findById(id).map(mapper::toDomain);
    }
}