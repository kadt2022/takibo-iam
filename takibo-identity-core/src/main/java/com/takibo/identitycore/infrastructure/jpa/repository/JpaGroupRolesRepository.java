package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.infrastructure.entity.GroupRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaGroupRolesRepository extends JpaRepository<GroupRoleEntity, UUID> {
    boolean existsBySpaceIdAndGroupIdAndRoleId(UUID spaceId, UUID groupId, UUID roleId);

    // variantes utiles si tu préfères
    Optional<GroupRoleEntity> findBySpaceIdAndGroupIdAndRoleId(UUID spaceId, UUID groupId, UUID roleId);

    // fallback explicite JPQL (équivalent à existsBy…)
    @Query("""
           select case when count(gr) > 0 then true else false end
           from GroupRoleEntity gr
           where gr.spaceId = :spaceId and gr.groupId = :groupId and gr.roleId = :roleId
           """)
    boolean existsTriple(@Param("spaceId") UUID spaceId,
                         @Param("groupId") UUID groupId,
                         @Param("roleId") UUID roleId);

    /**
     * Codes des rôles d'une nature donnée transmis par des groupes du space,
     * identifiés par leurs codes. Strictement situé : liens, groupes et rôles
     * appartiennent tous au même space.
     */
    @Query("""
           select distinct r.code
           from GroupRoleEntity gr
             join GroupEntity g on g.id = gr.groupId and g.spaceId = gr.spaceId
             join RoleEntity r on r.id = gr.roleId and r.spaceId = gr.spaceId
           where gr.spaceId = :spaceId
             and g.code in :groupCodes
             and r.roleNature = :nature
           """)
    List<String> findRoleCodesBySpaceAndGroupCodesAndNature(@Param("spaceId") UUID spaceId,
                                                            @Param("groupCodes") Collection<String> groupCodes,
                                                            @Param("nature") RoleNature nature);
}