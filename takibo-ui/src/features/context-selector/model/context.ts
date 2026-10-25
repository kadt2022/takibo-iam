import type { AccessibleSpace } from '@/features/spaces/model/space';

/**
 * Contexte actif du shell (récit UI 06A). Dans ce récit, le contexte réel est
 * TOUJOURS `ORGANIZATION` : la variante `SPACE` décrit la forme future du
 * contrat, elle n'est jamais fabriquée tant que l'échange sécurisé de token
 * ORGANIZATION → SPACE n'existe pas côté backend.
 */
export type CurrentContext =
  | { type: 'ORGANIZATION'; organizationId: string }
  | { type: 'SPACE'; organizationId: string; spaceId: string };

/**
 * État de la couture `selectSpace` : tant que l'échange n'existe pas, toute
 * demande d'ouverture d'un Space aboutit à `unsupported` — jamais à un faux
 * contexte local.
 */
export type SpaceSelectionState = { status: 'idle' } | { status: 'unsupported'; spaceId: string };

/**
 * Raison lisible d'un Space non sélectionnable, dérivée UNIQUEMENT des données
 * du contrat /me/spaces — aucun motif inventé.
 */
export function spaceUnavailabilityReason(space: AccessibleSpace): string {
  if (space.spaceStatus === 'SUSPENDED') {
    return 'Space suspendu';
  }
  if (space.spaceStatus !== 'ACTIVE') {
    return 'Accès temporairement impossible';
  }
  if (space.userStatus !== 'ACTIVE') {
    return 'Profil utilisateur indisponible';
  }
  return 'Accès temporairement impossible';
}
