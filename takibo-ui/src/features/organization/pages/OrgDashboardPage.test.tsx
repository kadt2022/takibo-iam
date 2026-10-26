import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { OrganizationSession } from '@/features/authentication/model/login';
import { OrgDashboardPage } from '@/features/organization/pages/OrgDashboardPage';
import { SessionContext } from '@/shared/security/session-context';

const fetchMock = vi.fn();

function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

// Valeurs volontairement distinctives : impossibles à confondre avec les
// données de démonstration encore présentes sur la page.
const SUMMARY = {
  organizationId: 'org-uuid',
  usersTotal: 4242,
  activeUsersTotal: 3737,
  spacesTotal: 99,
  oauthClientsTotal: 555,
  generatedAt: '2026-07-16T10:00:00Z',
};

const ORG_SPACES_PAGE = {
  content: [],
  page: 0,
  size: 1,
  totalElements: 88,
  totalPages: 88,
};

/** Chaque carte a sa source : le résumé (users) et l'inventaire (spaces). */
function routedFetch(url: string, summaryStatus = 200) {
  if (url.includes('/dashboard/summary')) {
    return Promise.resolve(jsonResponse(summaryStatus === 200 ? SUMMARY : {}, summaryStatus));
  }
  return Promise.resolve(jsonResponse(ORG_SPACES_PAGE));
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
    email: 'founder@takibo.io',
    subjectType: 'HUMAN',
    authMethod: 'PASSWORD',
    roles,
    groups: [],
    permissions: [],
  };
}

function renderAs(roles: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <SessionContext.Provider
        value={{ session: makeSession(roles), openSession: () => {}, closeSession: () => {} }}
      >
        <MemoryRouter>
          <OrgDashboardPage />
        </MemoryRouter>
      </SessionContext.Provider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('OrgDashboardPage — compteurs réels (UI 04)', () => {
  it('affiche les compteurs réels de l’organisation pour un Org Admin', async () => {
    fetchMock.mockImplementation((url: string) => routedFetch(url));

    renderAs(['R_ORG_ADMIN']);

    expect(await screen.findByText('4242')).toBeInTheDocument();
    // activeUsersTotal (3737) reste dans le contrat mais n'est PLUS affiché :
    // les statuts appartiennent aux écrans de détail, pas au dashboard.
    expect(screen.queryByText('3737')).not.toBeInTheDocument();
    // Clients OAuth2 : compteur réel du résumé (récit Dashboard 02).
    expect(screen.getByText('555')).toBeInTheDocument();
    // Spaces vient de l'inventaire administratif, pas du résumé.
    expect(await screen.findByText('88')).toBeInTheDocument();
    expect(screen.getByText('Indicateurs réels')).toBeInTheDocument();
    expect(screen.getByText('comptes distincts de l’organisation')).toBeInTheDocument();
    const rolesLabel = screen.getByText('Rôles');
    const groupsLabel = screen.getByText('Groupes');
    expect(rolesLabel.nextElementSibling).toHaveTextContent('0');
    expect(groupsLabel.nextElementSibling).toHaveTextContent('0');
    expect(screen.getAllByText('bientôt disponible')).toHaveLength(2);
    expect(rolesLabel.closest('a')).toBeNull();
    expect(groupsLabel.closest('a')).toBeNull();

    const urls = fetchMock.mock.calls.map((call) => call[0] as string);
    expect(urls).toContain('/api/v1/orgs/org-uuid/dashboard/summary');
    expect(urls.some((url) => url.includes('/api/v1/orgs/org-uuid/spaces?'))).toBe(true);
  });

  it('garde le compteur de Spaces même si le résumé est indisponible', async () => {
    // Non-régression : tant que le read-side dashboard n'est pas déployé, la
    // carte Spaces (surface déjà en place) doit continuer d'afficher son total.
    fetchMock.mockImplementation((url: string) => routedFetch(url, 404));

    renderAs(['R_ORG_ADMIN']);

    expect(await screen.findByText('88')).toBeInTheDocument();
    // Le libellé apparaît sur les cartes Spaces ET Clients OAuth2 (qui affiche
    // « — » quand le résumé est indisponible).
    expect(screen.getAllByText('dans l’organisation')).toHaveLength(2);
  });

  it('ne montre plus aucune tuile KPI de démonstration', async () => {
    fetchMock.mockImplementation((url: string) => routedFetch(url));

    renderAs(['R_ORG_OWNER']);

    await screen.findByText('4242');
    // La rangée démo a disparu : les nouvelles cartes Rôles et Groupes sont
    // des emplacements statiques à 0, sans tendance inventée.
    expect(screen.queryAllByText('vs période précédente')).toHaveLength(0);
  });

  it('masque toute la surface réelle quand la frontière refuse le résumé (403)', async () => {
    // Un rôle R_ORG_* décodé ne suffit pas : si le backend refuse le résumé,
    // la frontière fait autorité et la section disparaît (pas de tuiles « — »).
    fetchMock.mockImplementation((url: string) => routedFetch(url, 403));

    renderAs(['R_ORG_ADMIN']);

    await waitFor(() => expect(screen.queryByText('Indicateurs réels')).not.toBeInTheDocument());
    expect(screen.queryByText('comptes distincts de l’organisation')).not.toBeInTheDocument();
  });

  it('masque les compteurs réels pour un membre, sans appel réseau', () => {
    renderAs([]);

    expect(screen.queryByText('Indicateurs réels')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
