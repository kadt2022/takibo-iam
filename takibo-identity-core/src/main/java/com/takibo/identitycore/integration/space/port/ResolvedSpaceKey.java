package com.takibo.identitycore.integration.space.port;

import java.util.UUID;

/**
 * Résultat d'une résolution {@code orgCode + spaceCode -> orgId + spaceId}.
 * <p>
 * Volontairement composé uniquement de types neutres ({@link UUID} / {@link String})
 * pour ne jamais faire fuiter d'entité JPA du TMS vers TIS-CORE.
 * Les codes sont retournés sous leur forme canonique (normalisée).
 */
public record ResolvedSpaceKey(
        UUID orgId,
        UUID spaceId,
        String orgCode,
        String spaceCode
) {}
