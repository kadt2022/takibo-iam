import { primaryOrgRole } from '@/shared/identity/roles';
import { useSession } from '@/shared/security/session-context';

/**
 * Identité honnête de l'acteur courant, dérivée de la session ORGANIZATION.
 *
 * Récit UI 02 : plus aucune donnée fictive. Ne sont exposées que les données
 * réellement disponibles — le courriel et le code lisibles saisis au login, les
 * identifiants techniques (UUID) et les rôles réels du token. Aucun nom complet,
 * aucun nom commercial, aucune photo : ils n'existent pas encore côté backend.
 */
export interface OrganizationIdentity {
  email: string;
  /** Code lisible de l'organisation (saisi au login). */
  orgCode: string;
  /** UUID technique de l'organisation (LoginResponse). */
  organizationId: string;
  accountId: string;
  roles: string[];
  /** Code canonique du rôle principal (R_ORG_*), ou null. */
  roleCode: string | null;
  /** Libellé lisible du rôle principal. */
  roleLabel: string;
  context: 'ORGANIZATION';
  /** Initiale d'avatar dérivée du courriel (jamais d'un nom fabriqué). */
  avatarInitial: string;
  /** Epoch ms d'expiration de la preuve. */
  expiresAt: number;
}

/**
 * Couture unique du shell. N'est appelé que sous la garde de session : une
 * session ORGANIZATION active est donc toujours présente ici.
 */
export function useIdentity(): OrganizationIdentity {
  const { session } = useSession();
  if (!session) {
    throw new Error('useIdentity requiert une session ORGANIZATION active.');
  }

  const role = primaryOrgRole(session.roles);
  return {
    email: session.email,
    orgCode: session.orgCode,
    organizationId: session.organizationId,
    accountId: session.accountId,
    roles: session.roles,
    roleCode: role.code,
    roleLabel: role.label,
    context: 'ORGANIZATION',
    avatarInitial: (session.email.trim()[0] ?? '?').toUpperCase(),
    expiresAt: session.expiresAt,
  };
}
