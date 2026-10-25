import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import type { OrganizationSession } from '@/features/authentication/model/login';
import { OrgSettingsPage } from '@/features/organization/pages/OrgSettingsPage';
import { SessionContext } from '@/shared/security/session-context';

const SESSION: OrganizationSession = {
  accessToken: 'tok',
  tokenType: 'Bearer',
  expiresIn: 3600,
  expiresAt: Date.now() + 3_600_000,
  scopeLevel: 'ORGANIZATION',
  orgCode: 'takibo-finance',
  organizationId: 'org-uuid',
  accountId: 'acc-uuid',
  email: 'founder@takibo.io',
  subjectType: 'HUMAN',
  authMethod: 'PASSWORD',
  roles: ['R_ORG_OWNER'],
  groups: [],
  permissions: [],
};

describe('OrgSettingsPage', () => {
  it('situe l’organisation réelle de la session, jamais un nom fictif', () => {
    render(
      <SessionContext.Provider
        value={{ session: SESSION, openSession: () => {}, closeSession: () => {} }}
      >
        <OrgSettingsPage />
      </SessionContext.Provider>,
    );

    expect(screen.getByText('Configuration de takibo-finance.')).toBeInTheDocument();
    expect(screen.queryByText(/ACME/)).not.toBeInTheDocument();
  });
});
