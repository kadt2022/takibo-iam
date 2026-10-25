/**
 * Modèles réels des Spaces TAKIBO (récit UI 03). Deux surfaces, deux vérités :
 *   - {@link AccessibleSpace} : appartenance personnelle (GET /api/v1/me/spaces) ;
 *   - {@link OrganizationSpace} : inventaire administratif de l'organisation
 *     (GET /api/v1/orgs/{organizationId}/spaces).
 */

export type SpaceStatus = 'ACTIVE' | 'SUSPENDED' | 'CREATING' | 'DISABLED';

/** Un item de GET /api/v1/me/spaces — le rôle local n'est PAS exposé par ce contrat. */
export interface AccessibleSpace {
  spaceId: string;
  code: string;
  name: string;
  userId: string;
  spaceStatus: SpaceStatus;
  userStatus: string;
  selectable: boolean;
}

/** Enveloppe de GET /api/v1/me/spaces. */
export interface CurrentUserSpacesResponse {
  organizationId: string;
  items: AccessibleSpace[];
}

/** Un Space du catalogue organisationnel (GET /api/v1/orgs/{organizationId}/spaces). */
export interface OrganizationSpace {
  id: string;
  orgId: string;
  code: string;
  name: string;
  status: SpaceStatus;
  ownerAccountId: string;
  createdAt: string;
  updatedAt: string;
}

/** Page paginée de l'inventaire organisationnel. */
export interface OrganizationSpacePage {
  content: OrganizationSpace[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

const USER_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Actif',
  SUSPENDED: 'Suspendu',
  LOCKED: 'Verrouillé',
  DEACTIVATED: 'Désactivé',
  PENDING: 'En attente',
  INVITED: 'Invité',
};

/** Libellé lisible d'un statut d'utilisateur ; le code brut sert de repli honnête. */
export function userStatusLabel(status: string): string {
  return USER_STATUS_LABELS[status] ?? status;
}
