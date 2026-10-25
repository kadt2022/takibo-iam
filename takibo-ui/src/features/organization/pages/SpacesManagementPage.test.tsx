import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { OrganizationSession } from '@/features/authentication/model/login';
import { SpacesManagementPage } from '@/features/organization/pages/SpacesManagementPage';
import { SessionContext } from '@/shared/security/session-context';

const fetchMock = vi.fn();

function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function makeSession(roles: string[]): OrganizationSession {
  return {
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
    roles,
    groups: [],
    permissions: [],
  };
}

function renderAs(roles: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const ui: ReactNode = <SpacesManagementPage />;
  render(
    <QueryClientProvider client={queryClient}>
      <SessionContext.Provider
        value={{ session: makeSession(roles), openSession: () => {}, closeSession: () => {} }}
      >
        {ui}
      </SessionContext.Provider>
    </QueryClientProvider>,
  );
}

const SPACE_ROW = {
  id: 'sp1',
  orgId: 'org-uuid',
  code: 'finance',
  name: 'Finance',
  status: 'ACTIVE',
  ownerAccountId: 'aaaaaaaa-1111-2222-3333-444444444444',
  createdAt: '2026-01-15T10:00:00Z',
  updatedAt: '2026-02-20T10:00:00Z',
};

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('SpacesManagementPage (UI 03)', () => {
  it('masque la surface pour un compte sans autorité ORG, sans appel réseau', () => {
    renderAs([]);
    expect(screen.getByText(/réservée aux administrateurs/)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('masque la surface pour un R_SPACE_ADMIN seul', () => {
    renderAs(['R_SPACE_ADMIN']);
    expect(screen.getByText(/réservée aux administrateurs/)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('affiche l’inventaire pour un R_ORG_ADMIN et cible l’UUID organizationId', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ content: [SPACE_ROW], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    );

    renderAs(['R_ORG_ADMIN']);

    expect(await screen.findByText('Finance')).toBeInTheDocument();
    expect(screen.getByText(/1 Space ·/)).toBeInTheDocument();
    const url = fetchMock.mock.calls[0]![0] as string;
    expect(url).toContain('/api/v1/orgs/org-uuid/spaces?');
  });

  it('masque la surface sur un 403 backend, même pour un admin (défense en profondeur)', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}, 403));
    renderAs(['R_ORG_ADMIN']);
    expect(await screen.findByText(/ne permet pas de lister les Spaces/)).toBeInTheDocument();
  });
});
