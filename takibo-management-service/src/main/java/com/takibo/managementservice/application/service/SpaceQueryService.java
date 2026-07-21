package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.port.SpaceLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpaceQueryService {

  private final SpaceLookupPort spaces;

  @Transactional(readOnly = true)
  public boolean exists(UUID spaceId) {
    return spaces.existsById(spaceId);
  }

  @Transactional(readOnly = true)
  public Optional<UUID> findOrganizationId(UUID spaceId) {
    return spaces.findOrganizationId(spaceId);
  }
}
