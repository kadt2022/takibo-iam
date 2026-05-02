package com.takibo.managementservice.infrastructure.jpa.repository;

import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataSpaceRepository extends JpaRepository<SpaceEntity, UUID> {

    @Query("select s.status from SpaceEntity s where s.id = :id")
    Optional<SpaceStatus> findStatusById(@Param("id") UUID id);

    @Query("select s.statusUpdatedAt from SpaceEntity s where s.id = :id")
    Optional<Instant> findStatusUpdatedAtById(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update SpaceEntity s
              set s.status = :status,
                  s.statusReason = :reason,
                  s.statusUpdatedAt = :updatedAt
            where s.id = :id
           """)
    int updateStatus(@Param("id") UUID id,
                     @Param("status") SpaceStatus status,
                     @Param("reason") String reason,
                     @Param("updatedAt") Instant updatedAt);
}
