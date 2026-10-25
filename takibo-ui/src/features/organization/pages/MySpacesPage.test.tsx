import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { OrganizationSession } from '@/features/authentication/model/login';
import { MySpacesPage } from '@/features/organization/pages/MySpacesPage';
import { SessionContext } from '@/shared/security/session-context';

const fetchMock = vi.fn();

function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

const SESSION: OrganizationSession = {
  accessToken: 'tok',
  tokenType: 'Bearer',
  expiresIn: 3600,
  expiresAt: Date.now() + 3_600_000,
  scopeLevel: 'ORGANIZATION',
  orgCode: 'acme',
  organizationId: 'org-uuid',
  accountId: 'acc-uuid',
  email: 'john.doe@acme.com',
  subjectType: 'HUMAN',
  authMethod: 'PASSWORD',
  roles: [],
  groups: [],
  permissions: [],
};

function renderWithSession(ui: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <SessionContext.Provider
        value={{ session: SESSION, openSession: () => {}, closeSession: () => {} }}
      >
        {ui}
      </SessionContext.Provider>
    </QueryClientProvider>,
  );
}

function item(overrides: Record<string, unknown> = {}) {
  return {
    spaceId: 's1',
    code: 'finance',
    name: 'Finance',
    userId: 'u1',
    spaceStatus: 'ACTIVE',
    userStatus: 'ACTIVE',
    selectable: true,
    ...overrides,
  };
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('MySpacesPage (UI 03)', () => {
  it('affiche les Spaces réels et pilote « Ouvrir » via selectable', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        organizationId: 'org-uuid',
        items: [
          item({ name: 'Finance', selectable: true }),
          item({
            spaceId: 's2',
            code: 'support',
            name: 'Support',
            spaceStatus: 'SUSPENDED',
            selectable: false,
          }),
        ],
      }),
    );

    renderWithSession(<MySpacesPage />);

    expect(await screen.findByText('Finance')).toBeInTheDocument();
    expect(screen.getByText('Support')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ouvrir' })).toBeDisabled();
    expect(screen.getByText('Indisponible')).toBeInTheDocument();
    // Pas de colonne rôle : /me/spaces ne le retourne pas.
    expect(screen.queryByText(/Rôle/)).not.toBeInTheDocument();
  });

  it('affiche un état vide quand aucun Space', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ organizationId: 'org-uuid', items: [] }));
    renderWithSession(<MySpacesPage />);
    expect(await screen.findByText('Aucun Space accessible')).toBeInTheDocument();
  });

  it('affiche une erreur récupérable sur échec technique', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}, 500));
    renderWithSession(<MySpacesPage />);
    expect(await screen.findByText(/Impossible de charger vos Spaces/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Réessayer' })).toBeInTheDocument();
  });
});
