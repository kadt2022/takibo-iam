package com.takibo.managementservice.application.service;

import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceQueryService {

  private final JpaSpaceRepository spaceRepository;

  @Transactional(readOnly = true)
  public boolean exists(UUID spaceId) {
    return spaceRepository.existsById(spaceId);
  }
}
