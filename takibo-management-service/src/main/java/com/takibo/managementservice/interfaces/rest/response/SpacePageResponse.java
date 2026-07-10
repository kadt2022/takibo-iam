package com.takibo.managementservice.interfaces.rest.response;

import java.util.List;

/**
 * Enveloppe paginée alignée sur UserPageResponse (TIS-CORE) : mêmes champs, même sémantique.
 */
public record SpacePageResponse(
        List<SpaceSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
