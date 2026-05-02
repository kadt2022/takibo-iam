package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.infrastructure.entity.UserEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.UserJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpa;
    private final UserJpaMapper mapper;
    private final EntityManager em;  // ✅ AJOUTÉ

    @Override
    @Transactional
    public User save(User user) {
        UserEntity entity = mapper.toEntity(user);

        // ✅ CORRECTION: Récupérer orgId depuis Space
        UUID orgId = em.createQuery(
                        "SELECT s.orgId FROM SpaceEntity s WHERE s.id = :spaceId",
                        UUID.class
                )
                .setParameter("spaceId", user.getSpaceId().value())
                .getSingleResult();

        entity.setOrgId(orgId);

        UserEntity saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public void delete(User user) {
        jpa.deleteById(user.getId().value());
    }

    @Override
    @Transactional
    public void deleteById(UserId id) {
        jpa.deleteById(id.value());
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(SpaceId spaceId, String username) {
        return jpa.findBySpaceIdAndUsername(spaceId.value(), username)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(SpaceId spaceId, EmailAddress email) {
        return jpa.findBySpaceIdAndEmailIgnoreCase(spaceId.value(), email.value())
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByUsernameIgnoreCaseAndIdNot(SpaceId spaceId, String username, UUID excludeId) {
        return jpa.existsBySpaceIdAndUsernameIgnoreCaseAndIdNot(spaceId.value(), username, excludeId);
    }

    @Override
    public boolean existsByEmailIgnoreCaseAndIdNot(SpaceId spaceId, EmailAddress email, UUID excludeId) {
        return jpa.existsBySpaceIdAndEmailIgnoreCaseAndIdNot(spaceId.value(), email.value(), excludeId);
    }

    @Override
    public Page<User> findAllBySpace(SpaceId spaceId, Pageable pageable) {
        return jpa.findAllBySpaceId(spaceId.value(), pageable)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsBySpaceAndAccount(SpaceId spaceId, AccountId accountId) {
        return jpa.existsBySpaceIdAndAccountId(spaceId.value(), accountId.getValue());
    }
}