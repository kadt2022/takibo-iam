package com.takibo.identitycore.domain.repository;

import com.takibo.identitycore.domain.model.TakiboIdentity;
import com.takibo.identitycore.domain.vo.TakiboIdentityId;

import java.util.Optional;
import java.util.UUID;

public interface TakiboIdentityRepository {

    TakiboIdentity save(TakiboIdentity identity);

    void flush();

    Optional<TakiboIdentity> findById(TakiboIdentityId id);

    Optional<TakiboIdentity> findByOrgIdAndAccountId(UUID orgId, UUID accountId);

    boolean existsByOrgIdAndAccountId(UUID orgId, UUID accountId);

    Optional<UUID> lockAndFindIdentityIdByOrgIdAndAccountId(UUID orgId, UUID accountId);
}