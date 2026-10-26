export interface LoginCredentials {
  email: string;
  password: string;
  orgCode: string;
}

/**
 * Contrat de POST /api/v1/auth/login (LoginResponse de TIS-CORE, IAM 31).
 * En portée ORGANIZATION, spaceId et userId sont absents de la réponse :
 * le user local est une réalité de space.
 */
export interface LoginSession {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  scopeLevel: string;
  organizationId: string;
  accountId: string;
  spaceId?: string;
  userId?: string;
}

/** Claims situés du token humain TAKIBO (signé par TAS). */
export interface TakiboTokenClaims {
  sub?: string;
  exp?: number;
  iat?: number;
  subject_type?: string;
  auth_method?: string;
  takibo_scope_level?: string;
  takibo_tenant_source?: string;
  org_id?: string;
  space_id?: string;
  account_id?: string;
  user_id?: string;
  roles?: string[];
  groups?: string[];
  permissions?: string[];
}

/**
 * Couture unique de session côté UI (récit UI 02) : la LoginResponse + le
 * contexte saisi au formulaire + les claims validés, réunis en un seul objet.
 * Le shell consomme CETTE abstraction ; aucun composant ne redécode le JWT.
 *
 * Deux registres d'identité d'organisation, jamais confondus :
 *   - {@link orgCode} lisible (saisi au login, langage TIS-CORE) ;
 *   - {@link organizationId} UUID (routes TMS machine-first).
 */
export interface OrganizationSession {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  /** Epoch ms d'expiration de la preuve (claim exp si présent, sinon expiresIn). */
  expiresAt: number;
  scopeLevel: 'ORGANIZATION';
  orgCode: string;
  organizationId: string;
  accountId: string;
  email: string;
  subjectType: 'HUMAN';
  authMethod: string;
  roles: string[];
  groups: string[];
  permissions: string[];
}
