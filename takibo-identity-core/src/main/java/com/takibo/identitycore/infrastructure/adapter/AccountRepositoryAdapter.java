package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.infrastructure.entity.AccountEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.AccountJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountRepositoryAdapter implements AccountRepository {

    private final JpaAccountRepository jpa;
    private final AccountJpaMapper mapper;

    @Override
    public Optional<Account> findById(AccountId id) {
        return jpa.findById(id.getValue()).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findByEmail(EmailAddress email) {
        return jpa.findByEmailIgnoreCase(email.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findByEmail(OrganizationId orgId, EmailAddress email) {
        return jpa.findByOrgIdAndEmailIgnoreCase(orgId.getValue(), email.value())
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Account save(Account account) {
        AccountEntity entityToSave = mapper.toEntity(account);
        AccountEntity saved = jpa.save(entityToSave);
        return mapper.toDomain(saved);
    }
}
