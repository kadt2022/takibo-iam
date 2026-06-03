package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.GroupRole;
import com.takibo.identitycore.domain.repository.GroupRoleRepository;
import com.takibo.identitycore.infrastructure.entity.GroupRoleEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.GroupRoleJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupRolesRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;


@Component
public class GroupRoleRepositoryAdapter implements GroupRoleRepository {

    private final GroupRoleJpaMapper mapper;
    private final JpaGroupRolesRepository jpa;
    private final PlatformTransactionManager transactionManager;

    public GroupRoleRepositoryAdapter(GroupRoleJpaMapper mapper,
                                      JpaGroupRolesRepository jpa,
                                      PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.jpa = jpa;
        this.transactionManager = transactionManager;
    }

    @Override
    public boolean existsBySpaceIdAndGroupIdAndRoleId(UUID spaceId, UUID groupId, UUID roleId) {
        return jpa.existsBySpaceIdAndGroupIdAndRoleId(spaceId, groupId, roleId);
    }

    @Override
    public GroupRole save(GroupRole groupRole) {
        if (existsInNewTransaction(groupRole)) {
            return groupRole;
        }

        GroupRoleEntity entity = mapper.toEntity(groupRole);
        try {
            GroupRoleEntity saved = executeInNewTransaction(() -> jpa.saveAndFlush(entity), false);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            if (existsInNewTransaction(groupRole)) {
                return groupRole;
            }
            throw ex;
        }
    }

    private boolean existsInNewTransaction(GroupRole groupRole) {
        return executeInNewTransaction(() -> jpa.existsBySpaceIdAndGroupIdAndRoleId(
                groupRole.getSpaceId().value(),
                groupRole.getGroupId().value(),
                groupRole.getRoleId().value()), true);
    }

    private <T> T executeInNewTransaction(Supplier<T> action, boolean readOnly) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(readOnly);
        return template.execute(status -> action.get());
    }
}
