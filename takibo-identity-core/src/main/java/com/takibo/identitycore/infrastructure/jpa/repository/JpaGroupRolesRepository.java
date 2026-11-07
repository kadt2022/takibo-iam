package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.infrastructure.entity.GroupRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaGroupRolesRepository extends JpaRepository<GroupRoleEntity, UUID> {
    boolean existsBySpaceIdAndGroupIdAndRoleId(UUID spaceId, UUID groupId, UUID roleId);

    /**
     * Codes des rôles d'une nature donnée transmis par des groupes du space,
     * identifiés par leurs codes. Strictement situé — same org, same space :
     * liens, groupes et rôles appartiennent tous à la même frontière.
     */
    @Query("""
           select distinct r.code
           from GroupRoleEntity gr
             join GroupEntity g
               on g.orgId = gr.orgId
              and g.spaceId = gr.spaceId
              and g.id = gr.groupId
             join RoleEntity r
               on r.orgId = gr.orgId
              and r.spaceId = gr.spaceId
              and r.id = gr.roleId
           where gr.orgId = :orgId
             and gr.spaceId = :spaceId
             and g.code in :groupCodes
             and r.roleNature = :nature
           """)
    List<String> findRoleCodesByOrgAndSpaceAndGroupCodesAndNature(@Param("orgId") UUID orgId,
                                                                  @Param("spaceId") UUID spaceId,
                                                                  @Param("groupCodes") Collection<String> groupCodes,
                                                                  @Param("nature") RoleNature nature);
}