package com.takibo.identitycore.integration.space.port;

/**
 * Port de sortie pour la résolution des clés lisibles d'organisation et de Space.
 * <p>
 * TIS-CORE reçoit un {@code orgCode} et un {@code spaceCode} (langage humain) mais ne
 * connaît ni le catalogue des organisations/spaces ni les règles de normalisation :
 * il délègue la traduction à l'infrastructure (implémentée par le TMS).
 * <p>
 * Contrat : la résolution se fait TOUJOURS dans le périmètre d'une organisation
 * ({@code orgCode} puis {@code orgId + spaceCode}). Un Space n'est jamais résolu par
 * son code seul.
 */
public interface SpaceKeyResolutionCase {

    /**
     * Résout un couple de codes lisibles vers leurs identifiants techniques.
     *
     * @param orgCode   code lisible de l'organisation (brut, non normalisé)
     * @param spaceCode code lisible du Space dans cette organisation (brut, non normalisé)
     * @return les identifiants techniques résolus et les codes sous leur forme canonique
     * @throws com.takibo.identitycore.domain.exception.OrganizationNotFoundException si aucune organisation ne porte ce code
     * @throws com.takibo.identitycore.domain.exception.SpaceNotFoundException        si aucun Space ne porte ce code dans cette organisation
     */
    ResolvedSpaceKey resolve(String orgCode, String spaceCode);
}
