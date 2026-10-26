/**
 * Correspondances visuelles autorisées pour les rôles organisationnels.
 * Le code canonique (R_ORG_*) reste la vérité ; le label n'est qu'un affichage.
 */
const ORG_ROLE_LABELS: Record<string, string> = {
  R_ORG_OWNER: 'Propriétaire de l’organisation',
  R_ORG_ADMIN: 'Administrateur de l’organisation',
};

/** Priorité d'affichage quand le compte porte plusieurs rôles organisationnels. */
const ORG_ROLE_PRIORITY = ['R_ORG_OWNER', 'R_ORG_ADMIN'];

export interface OrgRole {
  /** Code canonique R_ORG_*, ou null si le token ne porte aucun rôle. */
  code: string | null;
  /** Libellé lisible (mappé si connu, sinon le code brut). */
  label: string;
}

export function primaryOrgRole(roles: string[]): OrgRole {
  const code = ORG_ROLE_PRIORITY.find((role) => roles.includes(role)) ?? roles[0] ?? null;
  if (!code) {
    return { code: null, label: 'Membre de l’organisation' };
  }
  return { code, label: ORG_ROLE_LABELS[code] ?? code };
}

/**
 * Autorité d'organisation : seul R_ORG_OWNER / R_ORG_ADMIN voit l'inventaire
 * administratif des Spaces. Un R_SPACE_ADMIN seul ne l'est pas (sa frontière est
 * un Space, pas l'organisation).
 */
export function isOrgAdmin(roles: string[]): boolean {
  return roles.includes('R_ORG_OWNER') || roles.includes('R_ORG_ADMIN');
}
