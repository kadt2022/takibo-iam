package com.takibo.identitycore.domain.rbac.repository;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;

import java.util.List;
import java.util.UUID;

public interface GovernanceRoleAssignmentRepository {

    /** @throws DuplicateAssignmentException if the assignment already exists */
    RoleAssignment saveGovernanceAssignment(RoleAssignment assignment);

    /**
     * Codes des rôles techniques assignés à un account, situés sur ce space
     * (les assignments org-level sans space sont inclus).
     */
    List<String> findAssignedTechnicalRoleCodes(UUID orgId, UUID spaceId, UUID accountId);

    /**
     * Assignations directes (TECHNICAL + GOVERNANCE) d'un account visibles dans ce
     * space — les assignments org-level sans space sont inclus, les BUSINESS exclus.
     */
    List<RoleAssignment> findDirectAssignments(UUID orgId, UUID spaceId, UUID accountId);

    /** Le code est-il déjà assigné à cet account, visible dans ce space ? */
    boolean existsAssignment(UUID orgId, UUID spaceId, UUID accountId, String roleCode);

    /**
     * Supprime les assignations directes strictement situées sur ce space
     * (les assignments org-level sans space ne sont pas touchés par une route space).
     * @return nombre de lignes supprimées (0 = idempotent)
     */
    int deleteAssignment(UUID orgId, UUID spaceId, UUID accountId, String roleCode);

    /** Nombre d'identités distinctes tenant ce code dans le space (org-level inclus). */
    long countIdentitiesHoldingRole(UUID orgId, UUID spaceId, String roleCode);

    /**
     * Assignations org-level uniquement (space_id IS NULL) — le pouvoir de portée
     * ORGANIZATION d'un account, indépendant de tout space (BUSINESS exclu).
     */
    List<RoleAssignment> findOrgLevelAssignments(UUID orgId, UUID accountId);

    /**
     * Supprime une assignation org-level (space_id IS NULL).
     * @return nombre de lignes supprimées (0 = idempotent)
     */
    int deleteOrgLevelAssignment(UUID orgId, UUID accountId, String roleCode);
}
