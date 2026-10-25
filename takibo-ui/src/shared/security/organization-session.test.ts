import { describe, expect, it } from 'vitest';

import type { LoginSession } from '@/features/authentication/model/login';
import {
  buildOrganizationSession,
  isSessionActive,
  SessionRejectedError,
} from '@/shared/security/organization-session';

/** Encode un JWT non signé (les tests ne vérifient jamais la signature). */
function makeToken(claims: Record<string, unknown>): string {
  const encode = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode(claims)}.sig`;
}

const HUMAN_ORG_CLAIMS: Record<string, unknown> = {
  subject_type: 'HUMAN',
  auth_method: 'PASSWORD',
  takibo_scope_level: 'ORGANIZATION',
  takibo_tenant_source: 'human_login',
  org_id: '9eb7b8d5-79c8-4ac5-b4b8-b61ad332390f',
  account_id: 'a1b2c3d4-0000-0000-0000-000000000000',
  roles: ['R_ORG_ADMIN'],
  groups: ['G_FINANCE'],
  permissions: ['P_READ'],
};

function makeResponse(
  claims: Record<string, unknown>,
  overrides: Partial<LoginSession> = {},
): LoginSession {
  return {
    accessToken: makeToken(claims),
    tokenType: 'Bearer',
    expiresIn: 3600,
    scopeLevel: 'ORGANIZATION',
    organizationId: '9eb7b8d5-79c8-4ac5-b4b8-b61ad332390f',
    accountId: 'a1b2c3d4-0000-0000-0000-000000000000',
    ...overrides,
  };
}

const ORIGIN = { orgCode: 'acme', email: 'john.doe@acme.com' };

describe('buildOrganizationSession — frontières de la preuve', () => {
  it('accepte une session humaine de portée ORGANIZATION', () => {
    const session = buildOrganizationSession(makeResponse(HUMAN_ORG_CLAIMS), ORIGIN);

    expect(session.scopeLevel).toBe('ORGANIZATION');
    expect(session.subjectType).toBe('HUMAN');
    expect(session.email).toBe('john.doe@acme.com');
    expect(session.roles).toEqual(['R_ORG_ADMIN']);
    expect(session.groups).toEqual(['G_FINANCE']);
    expect(session.permissions).toEqual(['P_READ']);
    expect(session.authMethod).toBe('PASSWORD');
  });

  it('conserve distinctement orgCode (lisible) et organizationId (UUID)', () => {
    const session = buildOrganizationSession(makeResponse(HUMAN_ORG_CLAIMS), ORIGIN);

    expect(session.orgCode).toBe('acme');
    expect(session.organizationId).toBe('9eb7b8d5-79c8-4ac5-b4b8-b61ad332390f');
    expect(session.orgCode).not.toBe(session.organizationId);
  });

  it('refuse un token non HUMAN', () => {
    const claims = { ...HUMAN_ORG_CLAIMS, subject_type: 'CLIENT_APP' };
    expect(() => buildOrganizationSession(makeResponse(claims), ORIGIN)).toThrow(
      SessionRejectedError,
    );
  });

  it('refuse un token de portée non ORGANIZATION', () => {
    const claims = { ...HUMAN_ORG_CLAIMS, takibo_scope_level: 'SPACE' };
    expect(() => buildOrganizationSession(makeResponse(claims), ORIGIN)).toThrow(
      SessionRejectedError,
    );
  });

  it('refuse un token portant space_id', () => {
    const claims = { ...HUMAN_ORG_CLAIMS, space_id: 's-uuid' };
    expect(() => buildOrganizationSession(makeResponse(claims), ORIGIN)).toThrow(
      SessionRejectedError,
    );
  });

  it('refuse un token portant user_id', () => {
    const claims = { ...HUMAN_ORG_CLAIMS, user_id: 'u-uuid' };
    expect(() => buildOrganizationSession(makeResponse(claims), ORIGIN)).toThrow(
      SessionRejectedError,
    );
  });

  it('refuse une réponse situant la session (spaceId présent)', () => {
    expect(() =>
      buildOrganizationSession(makeResponse(HUMAN_ORG_CLAIMS, { spaceId: 's-uuid' }), ORIGIN),
    ).toThrow(SessionRejectedError);
  });

  it('accepte l’absence JSON de spaceId et userId (comportement contractuel normal)', () => {
    const response = makeResponse(HUMAN_ORG_CLAIMS);
    expect('spaceId' in response).toBe(false);
    expect('userId' in response).toBe(false);
    expect(() => buildOrganizationSession(response, ORIGIN)).not.toThrow();
  });

  it('dérive expiresAt du claim exp quand il est présent', () => {
    const claims = { ...HUMAN_ORG_CLAIMS, exp: 2_000_000_000 };
    const session = buildOrganizationSession(makeResponse(claims), ORIGIN);
    expect(session.expiresAt).toBe(2_000_000_000 * 1000);
  });

  it('dérive expiresAt de expiresIn en l’absence de claim exp', () => {
    const now = 1_000_000;
    const session = buildOrganizationSession(makeResponse(HUMAN_ORG_CLAIMS), ORIGIN, now);
    expect(session.expiresAt).toBe(now + 3600 * 1000);
  });
});

describe('isSessionActive', () => {
  const session = buildOrganizationSession(
    makeResponse({ ...HUMAN_ORG_CLAIMS, exp: 2_000_000_000 }),
    ORIGIN,
  );

  it('est actif avant expiration', () => {
    expect(isSessionActive(session, 2_000_000_000 * 1000 - 1)).toBe(true);
  });

  it('est expiré après l’échéance', () => {
    expect(isSessionActive(session, 2_000_000_000 * 1000 + 1)).toBe(false);
  });

  it('est inactif sans session', () => {
    expect(isSessionActive(null)).toBe(false);
  });
});
