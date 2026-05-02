package com.takibo.managementservice.application.service;

import com.takibo.identitycore.integration.space.port.SpaceManagementCase;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SpaceManagementTmsAdapter implements SpaceManagementCase {

  private final SpaceQueryService spaceQueryService;
  private final JpaSpaceRepository spaceJpaRepository;

  @Override
  public boolean doesSpaceExist(SpaceId spaceId) {
    // SpaceId.value() doit retourner un UUID
    return spaceQueryService.exists(spaceId.value());
  }

  @Override
  public Optional<UUID> findOrgIdBySpaceId(SpaceId spaceId) {
    // Appel direct avec UUID, plus de String, plus de BIN_TO_UUID/UUID_TO_BIN
    return spaceJpaRepository.findOrgIdById(spaceId.value());
  }
}
