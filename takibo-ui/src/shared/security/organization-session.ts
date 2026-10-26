import type { LoginSession, OrganizationSession } from '@/features/authentication/model/login';
import { decodeTokenClaims } from '@/shared/security/jwt';

/**
 * Une preuve reçue ne correspond pas à un accès humain de portée ORGANIZATION.
 * Message volontairement uniforme : l'UI n'expose jamais la raison précise du
 * rejet (frontière côté backend, cause réelle en audit).
 */
export class SessionRejectedError extends Error {
  constructor(message = 'Cette connexion n’ouvre pas un accès organisationnel valide.') {
    super(message);
    this.name = 'SessionRejectedError';
  }
}

/** Contexte lisible saisi au formulaire, joint à la preuve. */
export interface SessionOrigin {
  orgCode: string;
  email: string;
}

function isPresent(value: string | undefined | null): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

/**
 * Construit la session à partir de la LoginResponse, du contexte saisi et des
 * claims validés. N'accepte QUE :
 *   subject_type = HUMAN, takibo_scope_level = ORGANIZATION,
 *   org_id + account_id présents, space_id + user_id absents.
 * L'absence de spaceId/userId dans la réponse est le comportement contractuel
 * normal en portée ORGANIZATION — jamais un null attendu.
 */
export function buildOrganizationSession(
  response: LoginSession,
  origin: SessionOrigin,
  now: number = Date.now(),
): OrganizationSession {
  const claims = decodeTokenClaims(response.accessToken);
  if (!claims) {
    throw new SessionRejectedError('Preuve illisible.');
  }

  if (claims.subject_type !== 'HUMAN') throw new SessionRejectedError();
  if (claims.takibo_scope_level !== 'ORGANIZATION') throw new SessionRejectedError();
  if (!isPresent(claims.org_id)) throw new SessionRejectedError();
  if (!isPresent(claims.account_id)) throw new SessionRejectedError();
  // Un token ORGANIZATION ne doit jamais être situé dans un space.
  if (isPresent(claims.space_id)) throw new SessionRejectedError();
  if (isPresent(claims.user_id)) throw new SessionRejectedError();
  // La réponse elle-même ne doit pas non plus situer la session.
  if (isPresent(response.spaceId) || isPresent(response.userId)) {
    throw new SessionRejectedError();
  }

  const expiresAt =
    typeof claims.exp === 'number' ? claims.exp * 1000 : now + response.expiresIn * 1000;

  return {
    accessToken: response.accessToken,
    tokenType: response.tokenType,
    expiresIn: response.expiresIn,
    expiresAt,
    scopeLevel: 'ORGANIZATION',
    orgCode: origin.orgCode,
    organizationId: response.organizationId || claims.org_id,
    accountId: response.accountId || claims.account_id,
    email: origin.email,
    subjectType: 'HUMAN',
    authMethod: claims.auth_method ?? 'PASSWORD',
    roles: claims.roles ?? [],
    groups: claims.groups ?? [],
    permissions: claims.permissions ?? [],
  };
}

/** Une session est active tant que sa preuve n'a pas expiré. */
export function isSessionActive(
  session: OrganizationSession | null,
  now: number = Date.now(),
): boolean {
  return !!session && session.expiresAt > now;
}
