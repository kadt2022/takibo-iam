package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.infrastructure.entity.AccountCredentialsEntity;
import com.takibo.identitycore.infrastructure.entity.AccountEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.AccountCredentialsJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaAccountCredentialsRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        AccountEntity accountEntity = em.find(
                AccountEntity.class,
                accountId
        );

        if (accountEntity == null) {
            throw new IllegalStateException("Account not found: " + accountId);
        }

        entity.setAccount(accountEntity);
        entity.setOrgId(orgId);
        entity.setAccountId(accountId);

        // ✅ CORRECTION: Marquer explicitement comme nouvelle pour éviter StaleObjectStateException
        // Puis utiliser persist() qui est approprié pour les nouvelles entités
        em.persist(entity);
        em.flush(); // Force la synchronisation avec la BD

        return mapper.toDomain(entity);
    }
}