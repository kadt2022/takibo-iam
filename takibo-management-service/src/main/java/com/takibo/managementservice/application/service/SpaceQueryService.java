package com.takibo.managementservice.application.service;

import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.managementservice.domain.mapper.SpaceMapper;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import com.takibo.managementservice.infrastructure.jpa.mapper.SpaceJpaMapper;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import com.takibo.managementservice.interfaces.rest.response.SpacePageResponse;
import com.takibo.managementservice.interfaces.rest.response.SpaceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Read-side des spaces d'une organisation. La frontière (token.org_id == path.orgId,
 * autorité ORG) est garantie en amont par le PolicyEvaluator ; ici la recherche reste
 * située par orgId — un space d'une autre org N'EXISTE PAS (404, jamais de 403 qui
 * confirmerait son existence ailleurs).
 */
@Service
@RequiredArgsConstructor
public class SpaceQueryService {

  private static final int MAX_PAGE_SIZE = 100;
  private static final Set<String> SORTABLE_FIELDS =
      Set.of("code", "name", "status", "createdAt", "updatedAt");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

  private final JpaSpaceRepository spaceRepository;
  private final SpaceJpaMapper spaceJpaMapper;
  private final SpaceMapper spaceMapper;

  @Transactional(readOnly = true)
  public boolean exists(UUID spaceId) {
    return spaceRepository.existsById(spaceId);
  }

  @Transactional(readOnly = true)
  public SpacePageResponse listSpaces(UUID orgId, SpaceStatus status, String search,
                                      int page, int size, String sort) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    PageRequest pageRequest = PageRequest.of(safePage, safeSize, parseSort(sort));

    Page<SpaceEntity> result;
    if (search == null || search.isBlank()) {
      result = spaceRepository.findPageByOrg(orgId, status, pageRequest);
    } else {
      String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
      result = spaceRepository.searchPageByOrg(orgId, status, pattern, pageRequest);
    }

    return new SpacePageResponse(
        result.getContent().stream()
            .map(spaceJpaMapper::toDomain)
            .map(spaceMapper::toSpaceSummaryResponse)
            .toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public SpaceResponse getSpace(UUID orgId, UUID spaceId) {
    return spaceRepository.findByIdAndOrgId(spaceId, orgId)
        .map(spaceJpaMapper::toDomain)
        .map(spaceMapper::toSpaceResponse)
        .orElseThrow(() -> new SpaceNotFoundException("Space not found in this organization: " + spaceId));
  }

  private static Sort parseSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return DEFAULT_SORT;
    }
    String[] parts = sort.split(",");
    String field = parts[0].trim();
    if (!SORTABLE_FIELDS.contains(field)) {
      throw new IllegalArgumentException("Unsupported sort field: " + field);
    }
    Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
        ? Sort.Direction.DESC
        : Sort.Direction.ASC;
    return Sort.by(direction, field);
  }
}
