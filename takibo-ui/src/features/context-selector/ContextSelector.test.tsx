import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { OrganizationSession } from '@/features/authentication/model/login';
import { ContextSelector } from '@/features/context-selector/components/ContextSelector';
import { SessionContext } from '@/shared/security/session-context';

const fetchMock = vi.fn();
const closeSessionMock = vi.fn();

function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

/** Items réels du contrat GET /api/v1/me/spaces — aucun champ inventé. */
const SPACES = {
  organizationId: 'org-uuid',
  items: [
    {
      spaceId: 's-finance',
      code: 'finance',
      name: 'Finance',
      userId: 'u1',
      spaceStatus: 'ACTIVE',
      userStatus: 'ACTIVE',
      selectable: true,
    },
    {
      spaceId: 's-support',
      code: 'support',
      name: 'Support',
      userId: 'u2',
      spaceStatus: 'SUSPENDED',
      userStatus: 'ACTIVE',
      selectable: false,
    },
    {
      spaceId: 's-rh',
      code: 'rh',
      name: 'RH',
      userId: 'u3',
      spaceStatus: 'ACTIVE',
      userStatus: 'SUSPENDED',
      selectable: false,
    },
  ],
};

const SESSION: OrganizationSession = {
  accessToken: 'org-token-secret',
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

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{location.pathname}</span>;
}

function renderSelector(
  session = SESSION,
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionContext.Provider
        value={{ session, openSession: () => {}, closeSession: closeSessionMock }}
      >
        <MemoryRouter initialEntries={['/app/my-spaces']}>
          <ContextSelector />
          <LocationProbe />
        </MemoryRouter>
      </SessionContext.Provider>
    </QueryClientProvider>,
  );
}

function trigger() {
  return screen.getByRole('button', { name: /takibo-finance/ });
}

async function openMenu(user: ReturnType<typeof userEvent.setup>) {
  await user.click(trigger());
  return screen.getByRole('menu', { name: 'Changer de contexte' });
}

beforeEach(() => {
  fetchMock.mockReset();
  closeSessionMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ContextSelector — déclencheur (UI 06A)', () => {
  it('affiche le contexte Organisation courant sur le bouton', () => {
    renderSelector();

    const button = trigger();
    expect(button).toHaveTextContent('takibo-finance');
    expect(button).toHaveTextContent('Organisation');
    expect(button).toHaveAttribute('aria-haspopup', 'menu');
    expect(button).toHaveAttribute('aria-expanded', 'false');
  });

  it('ouvre et ferme le menu au clic', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    renderSelector();

    await openMenu(user);
    expect(trigger()).toHaveAttribute('aria-expanded', 'true');

    await user.click(trigger());
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
  });

  it('n’interroge pas /me/spaces tant que le menu est fermé', () => {
    renderSelector();

    expect(fetchMock).not.toHaveBeenCalled();
  });
  it('isole le cache des Spaces entre deux sessions successives', async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const otherSession: OrganizationSession = {
      ...SESSION,
      accessToken: 'other-token-secret',
      expiresAt: SESSION.expiresAt + 1,
      orgCode: 'other-org',
      organizationId: 'other-org-uuid',
      accountId: 'other-account-uuid',
    };
    const otherSpaces = {
      organizationId: 'other-org-uuid',
      items: [
        {
          spaceId: 's-engineering',
          code: 'engineering',
          name: 'Engineering',
          userId: 'u4',
          spaceStatus: 'ACTIVE',
          userStatus: 'ACTIVE',
          selectable: true,
        },
      ],
    };

    fetchMock.mockResolvedValueOnce(jsonResponse(SPACES));
    const firstRender = renderSelector(SESSION, queryClient);
    await openMenu(user);
    expect(await screen.findByText('Finance')).toBeInTheDocument();
    firstRender.unmount();

    fetchMock.mockResolvedValueOnce(jsonResponse(otherSpaces));
    renderSelector(otherSession, queryClient);
    await user.click(screen.getByRole('button', { name: /other-org/ }));

    expect(await screen.findByText('Engineering')).toBeInTheDocument();
    expect(screen.queryByText('Finance')).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect((fetchMock.mock.calls[1]![1] as RequestInit).headers).toMatchObject({
      Authorization: 'Bearer other-token-secret',
    });
  });
});

describe('ContextSelector — options (UI 06A)', () => {
  it('n’énumère QUE les Spaces — le contexte Organisation n’est pas répété dans le menu', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    renderSelector();

    await openMenu(user);
    await screen.findByText('Finance');

    // La carte-déclencheur porte déjà le contexte courant ; le menu liste
    // uniquement les Spaces accessibles.
    expect(screen.queryByRole('menuitem', { name: /Organisation/ })).not.toBeInTheDocument();
    expect(screen.getAllByRole('menuitem')).toHaveLength(3);
    expect(trigger()).toHaveTextContent('Organisation');
  });

  it('charge les Spaces depuis GET /api/v1/me/spaces avec le bearer token', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    renderSelector();

    await openMenu(user);
    expect(await screen.findByText('Finance')).toBeInTheDocument();

    const call = fetchMock.mock.calls[0]!;
    expect(call[0]).toBe('/api/v1/me/spaces');
    expect((call[1] as RequestInit).headers).toMatchObject({
      Authorization: 'Bearer org-token-secret',
    });
  });

  it('affiche le nom et le statut réels de chaque Space', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    renderSelector();

    await openMenu(user);

    expect(await screen.findByText('Finance')).toBeInTheDocument();
    expect(screen.getByText('finance')).toBeInTheDocument();
    expect(screen.getAllByText('Actif')).toHaveLength(2);
    expect(screen.getByText('Suspendu')).toBeInTheDocument();
  });

  it('rend un Space selectable disponible et un Space non selectable désactivé avec sa raison', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    renderSelector();

    await openMenu(user);

    const finance = await screen.findByRole('menuitem', { name: /Finance/ });
    expect(finance).not.toHaveAttribute('aria-disabled');

    // Désactivé mais VISIBLE et lisible par les technologies d'assistance.
    const support = screen.getByRole('menuitem', { name: /Support/ });
    expect(support).toHaveAttribute('aria-disabled', 'true');
    expect(support).toHaveTextContent('Space suspendu');

    const rh = screen.getByRole('menuitem', { name: /RH/ });
    expect(rh).toHaveAttribute('aria-disabled', 'true');
    expect(rh).toHaveTextContent('Profil utilisateur indisponible');
  });

  it('cliquer un Space selectable ne fabrique AUCUN contexte local : message sobre, pas de navigation', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    renderSelector();

    await openMenu(user);
    await user.click(await screen.findByRole('menuitem', { name: /Finance/ }));

    // La couture unique selectSpace signale « unsupported » — rien d'autre.
    expect(
      screen.getByText(
        'L’ouverture sécurisée du Space sera disponible avec l’établissement du contexte Space.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/app/my-spaces');
    expect(trigger()).toHaveTextContent('Organisation');
    expect(closeSessionMock).not.toHaveBeenCalled();
  });

  it('cliquer un Space désactivé ne fait rien', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    renderSelector();

    await openMenu(user);
    await user.click(screen.getByRole('menuitem', { name: /Support/ }));

    expect(
      screen.queryByText(
        'L’ouverture sécurisée du Space sera disponible avec l’établissement du contexte Space.',
      ),
    ).not.toBeInTheDocument();
  });

  it('n’écrit aucun token dans le stockage navigateur', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    renderSelector();

    await openMenu(user);
    await user.click(await screen.findByRole('menuitem', { name: /Finance/ }));

    const stored = [
      ...Object.keys(localStorage).map((key) => `${key}=${localStorage.getItem(key)}`),
      ...Object.keys(sessionStorage).map((key) => `${key}=${sessionStorage.getItem(key)}`),
    ].join(';');
    expect(stored).not.toContain('org-token-secret');
    expect(document.cookie).not.toContain('org-token-secret');
    expect(screen.getByTestId('location')).not.toHaveTextContent('org-token-secret');
  });
});

describe('ContextSelector — états du contrat /me/spaces (UI 06A)', () => {
  it('affiche le chargement pendant la requête', async () => {
    const user = userEvent.setup();
    fetchMock.mockImplementation(() => new Promise(() => {}));
    renderSelector();

    await openMenu(user);

    expect(screen.getByText('Chargement des Spaces…')).toBeInTheDocument();
  });

  it('affiche l’état vide sans inventer de Space', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse({ organizationId: 'org-uuid', items: [] }));
    renderSelector();

    await openMenu(user);

    expect(
      await screen.findByText('Votre compte n’a de profil dans aucun Space.'),
    ).toBeInTheDocument();
  });

  it('affiche l’erreur technique et permet le réessai', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse({}, 500));
    renderSelector();

    await openMenu(user);
    // Le hook fait UN retry automatique avec le backoff react-query (~1 s) :
    // le timeout doit dépasser ce délai, sinon le test court contre le backoff.
    expect(
      await screen.findByText('Impossible de charger vos Spaces.', undefined, { timeout: 4000 }),
    ).toBeInTheDocument();

    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));

    expect(await screen.findByText('Finance')).toBeInTheDocument();
  });

  it('403 : accès indisponible, SANS déconnexion automatique ni réessai', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse({}, 403));
    renderSelector();

    await openMenu(user);

    expect(
      await screen.findByText('Accès aux Spaces indisponible pour ce contexte.'),
    ).toBeInTheDocument();
    expect(closeSessionMock).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('401 : ferme la session (retour au login par la garde), sans réessai', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse({}, 401));
    renderSelector();

    await openMenu(user);

    await waitFor(() => expect(closeSessionMock).toHaveBeenCalled());
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('ContextSelector — clavier et ARIA (UI 06A)', () => {
  it('focus la recherche à l’ouverture, flèches pour circuler dans les options', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    renderSelector();

    await openMenu(user);
    await screen.findByText('Finance');

    const search = screen.getByRole('textbox', { name: 'Rechercher un Space' });
    await waitFor(() => expect(search).toHaveFocus());

    const finance = screen.getByRole('menuitem', { name: /Finance/ });
    await user.keyboard('{ArrowDown}');
    expect(finance).toHaveFocus();

    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: /Support/ })).toHaveFocus();

    await user.keyboard('{ArrowUp}');
    expect(finance).toHaveFocus();
  });

  it('la recherche filtre les Spaces par nom ou code', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    renderSelector();

    await openMenu(user);
    await screen.findByText('Finance');

    await user.type(screen.getByRole('textbox', { name: 'Rechercher un Space' }), 'fin');

    expect(screen.getByRole('menuitem', { name: /Finance/ })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: /Support/ })).not.toBeInTheDocument();

    await user.clear(screen.getByRole('textbox', { name: 'Rechercher un Space' }));
    await user.type(screen.getByRole('textbox', { name: 'Rechercher un Space' }), 'zzz');
    expect(screen.getByText(/Aucun Space ne correspond à/)).toBeInTheDocument();
  });

  it('Escape ferme le menu et restaure le focus sur le bouton', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(SPACES));
    renderSelector();

    await openMenu(user);
    await user.keyboard('{Escape}');

    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(trigger()).toHaveFocus();
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
  });
});
