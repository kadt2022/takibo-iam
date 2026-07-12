package com.takibo.identitycore.domain.rbac.repository;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;

import java.util.List;
import java.util.UUID;

public interface GovernanceGroupAssignmentRepository {

    /** @throws DuplicateAssignmentException if the assignment already exists */
    GroupAssignment saveGovernanceAssignment(GroupAssignment assignment);

    /**
     * Memberships directs (TECHNICAL + GOVERNANCE) d'un account visibles dans ce
     * space — les memberships org-level sans space sont inclus, les BUSINESS exclus.
     */
    List<GroupAssignment> findDirectMemberships(UUID orgId, UUID spaceId, UUID accountId);

    /** Le membership existe-t-il déjà pour cet account, visible dans ce space ? */
    boolean existsMembership(UUID orgId, UUID spaceId, UUID accountId, String groupCode);

    /**
     * Supprime les memberships strictement situés sur ce space.
     * @return nombre de lignes supprimées (0 = idempotent)
     */
    int deleteMembership(UUID orgId, UUID spaceId, UUID accountId, String groupCode);

    /** Nombre d'identités distinctes membres de ce groupe dans le space (org-level inclus). */
    long countIdentitiesInGroup(UUID orgId, UUID spaceId, String groupCode);

    /**
     * Memberships org-level uniquement (space_id IS NULL) — les groupes de portée
     * ORGANIZATION d'un account, indépendants de tout space (BUSINESS exclu).
     */
    List<GroupAssignment> findOrgLevelMemberships(UUID orgId, UUID accountId);

    /**
     * Supprime un membership org-level (space_id IS NULL).
     * @return nombre de lignes supprimées (0 = idempotent)
     */
    int deleteOrgLevelMembership(UUID orgId, UUID accountId, String groupCode);
}
