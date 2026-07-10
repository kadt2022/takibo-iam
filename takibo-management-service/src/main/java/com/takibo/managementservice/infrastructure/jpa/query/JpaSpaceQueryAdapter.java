package com.takibo.managementservice.infrastructure.jpa.query;

import com.takibo.managementservice.application.query.port.SpaceQueryCase;
import com.takibo.managementservice.application.query.result.SpaceDetailsResult;
import com.takibo.managementservice.application.query.result.SpacePageResult;
import com.takibo.managementservice.domain.exception.SpaceNotFoundException;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaSpaceQueryAdapter implements SpaceQueryCase {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("code", "name", "status", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final JpaSpaceRepository spaceRepository;
    private final JpaSpaceQueryMapper mapper;

    @Override
    public SpacePageResult listSpaces(UUID orgId, SpaceStatus status, String search,
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

        return new SpacePageResult(
                result.getContent().stream().map(mapper::toSummaryResult).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public SpaceDetailsResult getSpace(UUID orgId, UUID spaceId) {
        // Recherche située dès la requête : jamais de findById suivi d'un contrôle d'org.
        return spaceRepository.findByIdAndOrgId(spaceId, orgId)
                .map(mapper::toDetailsResult)
                .orElseThrow(() -> new SpaceNotFoundException(orgId, spaceId));
    }

    // Tri strict : "field", "field,asc" ou "field,desc" — tout le reste est refusé,
    // aucune direction inconnue ne retombe silencieusement sur ASC.
    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String[] parts = sort.split(",", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("Unsupported sort expression: " + sort);
        }
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Unsupported sort field: " + field);
        }
        Sort.Direction direction = parts.length == 2
                ? parseDirection(parts[1])
                : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }

    private static Sort.Direction parseDirection(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException("Unsupported sort direction: " + raw);
        };
    }
}
