package com.takibo.identitycore.domain.repository;

import com.takibo.identitycore.domain.model.GroupRole;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GroupRoleRepository {
    boolean existsBySpaceIdAndGroupIdAndRoleId(UUID spaceId, UUID groupId, UUID roleId);

    /**
     * Codes des rôles GOVERNANCE transmis par les groupes du space identifiés
     * par leurs codes. Les rôles BUSINESS liés à un groupe sont ignorés :
     * l'héritage effectif ne transmet que du pouvoir d'administration.
     */
    List<String> findGovernanceRoleCodesByGroups(UUID spaceId, Collection<String> groupCodes);

    GroupRole save(GroupRole link);
}
