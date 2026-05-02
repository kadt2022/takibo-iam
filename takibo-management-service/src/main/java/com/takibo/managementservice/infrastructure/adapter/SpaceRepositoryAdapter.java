package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.domain.repository.SpaceRepository;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import com.takibo.managementservice.infrastructure.jpa.mapper.SpaceJpaMapper;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import com.takibo.managementservice.infrastructure.jpa.repository.SpringDataSpaceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(Transactional.TxType.MANDATORY)
public class SpaceRepositoryAdapter implements SpaceRepository {

    private final SpringDataSpaceRepository jpa;
    private final JpaSpaceRepository jpaSpaceRepository;
    private final SpaceJpaMapper mapper;

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<SpaceStatus> findStatusById(UUID id) {
        return jpa.findStatusById(id);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<Instant> findStatusUpdatedAtById(UUID id) {
        return jpa.findStatusUpdatedAtById(id);
    }

    @Override
    public int updateStatus(UUID id, SpaceStatus status, String reason, Instant updatedAt) {
        return jpa.updateStatus(id, status, reason, updatedAt);
    }

    @Override
    public Space save(Space space) {
        SpaceEntity entity = mapper.toEntity(space);
        SpaceEntity saved = jpa.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public boolean existsByOrgIdAndCode(UUID orgId, String code) {
        return jpaSpaceRepository.existsByOrgIdAndCode(orgId, code);
    }

    @Override
    public Optional<Space> findByOrgIdAndCode(UUID orgId, String code) {
        return jpaSpaceRepository.findByOrgIdAndCode(orgId, code)
                .map(mapper::toDomain);
    }
}
