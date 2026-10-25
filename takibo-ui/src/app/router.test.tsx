import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { UserEvent } from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AppProviders } from '@/app/providers';
import { routes } from '@/app/router';

function makeToken(claims: Record<string, unknown>): string {
  const encode = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode(claims)}.sig`;
}

const OK_RESPONSE = {
  accessToken: makeToken({
    subject_type: 'HUMAN',
    takibo_scope_level: 'ORGANIZATION',
    auth_method: 'PASSWORD',
    org_id: 'org-uuid',
    account_id: 'acc-uuid',
    roles: ['R_ORG_ADMIN'],
    groups: [],
    permissions: [],
  }),
  tokenType: 'Bearer',
  expiresIn: 3600,
  scopeLevel: 'ORGANIZATION',
  organizationId: 'org-uuid',
  accountId: 'acc-uuid',
};

const fetchMock = vi.fn();

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return router;
}

const ME_SPACES_RESPONSE = {
  organizationId: 'org-uuid',
  items: [
    {
      spaceId: 's1',
      code: 'finance',
      name: 'Finance',
      userId: 'u1',
      spaceStatus: 'ACTIVE',
      userStatus: 'ACTIVE',
      selectable: true,
    },
    {
      spaceId: 's2',
      code: 'support',
      name: 'Support',
      userId: 'u1',
      spaceStatus: 'SUSPENDED',
      userStatus: 'ACTIVE',
      selectable: false,
    },
  ],
};

const ORG_SPACES_RESPONSE = {
  content: [],
  page: 0,
  size: 1,
  totalElements: 3,
  totalPages: 3,
};

const DASHBOARD_SUMMARY_RESPONSE = {
  organizationId: 'org-uuid',
  usersTotal: 2,
  activeUsersTotal: 2,
  spacesTotal: 3,
  oauthClientsTotal: 1,
  generatedAt: '2026-07-16T10:00:00Z',
};

/** Dispatch par URL : login, /me/spaces, résumé dashboard, inventaire org. */
function routedFetch(url: string) {
  const body =
    url === '/api/v1/me/spaces'
      ? ME_SPACES_RESPONSE
      : url.includes('/dashboard/summary')
        ? DASHBOARD_SUMMARY_RESPONSE
        : url.startsWith('/api/v1/orgs/')
          ? ORG_SPACES_RESPONSE
          : OK_RESPONSE;
  return Promise.resolve({ ok: true, status: 200, json: async () => body });
}

/** Passe par le vrai formulaire de login pour ouvrir une session ORGANIZATION. */
async function renderLoggedIn(user: UserEvent) {
  fetchMock.mockImplementation((url: string) => routedFetch(url));
  const router = renderAt('/login');
  await user.type(screen.getByLabelText('Organisation'), 'acme');
  await user.type(screen.getByLabelText('Adresse courriel'), 'john.doe@acme.com');
  await user.type(screen.getByLabelText('Mot de passe'), 'secret123');
  await user.click(screen.getByRole('button', { name: 'Se connecter' }));
  await screen.findAllByText('john.doe@acme.com');
  return router;
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  localStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe('router — entrée et gardes', () => {
  it('affiche l’écran de connexion sur /login', async () => {
    renderAt('/login');
    expect(
      await screen.findByRole('heading', { name: 'Bienvenue dans TAKIBO' }),
    ).toBeInTheDocument();
  });

  it('protège /app/** : sans session, redirige vers /login', async () => {
    const router = renderAt('/app/dashboard');
    expect(
      await screen.findByRole('heading', { name: 'Bienvenue dans TAKIBO' }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('rend un état « introuvable » sur une route inconnue', async () => {
    renderAt('/nowhere');
    expect(await screen.findByRole('heading', { name: 'Page introuvable' })).toBeInTheDocument();
  });
});

describe('shell sous session ORGANIZATION', () => {
  it('ouvre le tableau de bord réel et rend la frontière visible', async () => {
    const user = userEvent.setup();
    await renderLoggedIn(user);

    // La frontière est portée par la carte du sélecteur (Sidebar) et l'identité
    // par la TopBar — plus de badge « Contexte actuel » ni de barre de recherche.
    expect(screen.getAllByText('john.doe@acme.com').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Organisation').length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Tableau de bord' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mes Spaces' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Gestion des Spaces' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Utilisateurs' })).not.toBeInTheDocument();
    // Récit UI 06A : Rôles et Groupes ne figurent PAS dans le menu Organisation —
    // ce sont des surfaces situées par Space, réservées au futur contexte SPACE.
    expect(screen.queryByRole('link', { name: 'Rôles' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Groupes' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Paramètres' })).toBeInTheDocument();
  });

  it('navigue vers « Mes Spaces » et garde l’ouverture de space désactivée', async () => {
    const user = userEvent.setup();
    await renderLoggedIn(user);

    await user.click(screen.getByRole('link', { name: 'Mes Spaces' }));

    expect(await screen.findByRole('heading', { name: 'Mes Spaces' })).toBeInTheDocument();
    expect(screen.getByText('Finance')).toBeInTheDocument();
    const [openButton] = screen.getAllByRole('button', { name: 'Ouvrir' });
    expect(openButton!).toBeDisabled();
    expect(screen.getByText('Indisponible')).toBeInTheDocument();
  });

  it('replie et déploie le menu latéral', async () => {
    const user = userEvent.setup();
    await renderLoggedIn(user);

    await user.click(screen.getByRole('button', { name: 'Réduire le menu' }));
    expect(screen.getByRole('button', { name: 'Déployer le menu' })).toBeInTheDocument();
  });
});
