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

        AccountCredentialsEntity entity = mapper.toEntity(domain, orgId);

        UUID accountId = domain.getAccountId().getValue();
        AccountEntity accountRef = em.getReference(AccountEntity.class, accountId);

        entity.setAccount(accountRef);
        entity.setOrgId(orgId);
        entity.setAccountId(accountId);

        return mapper.toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<AccountCredentials> find(OrganizationId orgId, AccountId accountId) {
        AccountCredentialsEntity.AccountCredentialsId id =
                new AccountCredentialsEntity.AccountCredentialsId(orgId.getValue(), accountId.getValue());
        return jpa.findById(id).map(mapper::toDomain);
    }
}