package com.takibo.managementservice.application.query.result;

import java.util.List;

/**
 * Page applicative de spaces — même sémantique que l'enveloppe REST, sans en
 * dépendre.
 */
public record SpacePageResult(
        List<SpaceSummaryResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
