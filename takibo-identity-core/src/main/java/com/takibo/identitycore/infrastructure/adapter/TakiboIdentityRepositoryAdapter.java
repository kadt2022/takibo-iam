package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.TakiboIdentity;
import com.takibo.identitycore.domain.repository.TakiboIdentityRepository;
import com.takibo.identitycore.domain.vo.TakiboIdentityId;
import com.takibo.identitycore.infrastructure.entity.TakiboIdentityEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.TakiboIdentityJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaTakiboIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TakiboIdentityRepositoryAdapter implements TakiboIdentityRepository {

    private final JpaTakiboIdentityRepository jpaRepository;
    private final TakiboIdentityJpaMapper mapper;

    @Override
    @Transactional
    public TakiboIdentity save(TakiboIdentity identity) {
        TakiboIdentityEntity entity = mapper.toEntity(identity);
        jpaRepository.saveAndFlush(entity);

        return identity;
    }

    @Override
    @Transactional
    public void flush() {
        jpaRepository.flush();
    }

    @Override
    public Optional<TakiboIdentity> findById(TakiboIdentityId id) {
        return jpaRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<TakiboIdentity> findByOrgIdAndAccountId(UUID orgId, UUID accountId) {
        return jpaRepository.findByOrgIdAndAccountId(orgId, accountId)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrgIdAndAccountId(UUID orgId, UUID accountId) {
        return jpaRepository.existsByOrgIdAndAccountId(orgId, accountId);
    }

    @Override
    public Optional<UUID> lockAndFindIdentityIdByOrgIdAndAccountId(UUID orgId, UUID accountId) {
        return jpaRepository.lockByOrgIdAndAccountId(orgId, accountId)
                .map(TakiboIdentityEntity::getIdentityId);
    }
}