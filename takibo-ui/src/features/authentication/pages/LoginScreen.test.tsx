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

const OK_TOKEN = makeToken({
  subject_type: 'HUMAN',
  takibo_scope_level: 'ORGANIZATION',
  auth_method: 'PASSWORD',
  org_id: 'org-uuid',
  account_id: 'acc-uuid',
  roles: ['R_ORG_ADMIN'],
  groups: [],
  permissions: [],
});

const OK_RESPONSE = {
  accessToken: OK_TOKEN,
  tokenType: 'Bearer',
  expiresIn: 3600,
  scopeLevel: 'ORGANIZATION',
  organizationId: 'org-uuid',
  accountId: 'acc-uuid',
};

const fetchMock = vi.fn();

function okFetch() {
  return { ok: true, status: 200, json: async () => OK_RESPONSE };
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return router;
}

async function fillCredentials(user: UserEvent) {
  await user.type(screen.getByLabelText('Organisation'), 'acme');
  await user.type(screen.getByLabelText('Adresse courriel'), 'john.doe@acme.com');
  await user.type(screen.getByLabelText('Mot de passe'), 'secret123');
}

function localStorageDump(): string {
  let dump = '';
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key) dump += `${key}=${localStorage.getItem(key)};`;
  }
  return dump;
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

describe('LoginScreen — connexion réelle (UI 02)', () => {
  it('présente exactement trois champs, sans spaceCode', () => {
    renderAt('/login');

    expect(screen.getByLabelText('Organisation')).toBeInTheDocument();
    expect(screen.getByLabelText('Adresse courriel')).toBeInTheDocument();
    expect(screen.getByLabelText('Mot de passe')).toBeInTheDocument();
    expect(screen.queryByLabelText(/space/i)).not.toBeInTheDocument();
  });

  it('poste orgCode + email + password et jamais spaceCode', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(okFetch());
    renderAt('/login');

    await fillCredentials(user);
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    await screen.findAllByText('john.doe@acme.com');

    const call = fetchMock.mock.calls[0]!;
    expect(call[0]).toBe('/api/v1/auth/login');
    const body = JSON.parse((call[1] as RequestInit).body as string);
    expect(body).toEqual({ orgCode: 'acme', email: 'john.doe@acme.com', password: 'secret123' });
    expect(body).not.toHaveProperty('spaceCode');
  });

  it('ouvre le shell sur l’identité réelle et ne persiste aucun token', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(okFetch());
    renderAt('/login');

    await fillCredentials(user);
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    // Le shell est ouvert : la TopBar porte l'identité réelle de la session.
    expect((await screen.findAllByText('john.doe@acme.com')).length).toBeGreaterThan(0);

    const dump = localStorageDump();
    expect(dump).not.toContain('accessToken');
    expect(dump).not.toContain(OK_TOKEN);
  });

  it('affiche un message uniforme sur 401', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue({ ok: false, status: 401, json: async () => ({}) });
    renderAt('/login');

    await fillCredentials(user);
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Impossible de valider cette connexion.',
    );
  });

  it('bloque les soumissions multiples pendant la connexion', async () => {
    const user = userEvent.setup();
    let resolveFetch: (value: unknown) => void = () => {};
    fetchMock.mockReturnValue(
      new Promise((resolve) => {
        resolveFetch = resolve;
      }),
    );
    renderAt('/login');

    await fillCredentials(user);
    const submit = screen.getByRole('button', { name: 'Se connecter' });
    await user.click(submit);

    expect(submit).toBeDisabled();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolveFetch(okFetch());
    await screen.findAllByText('john.doe@acme.com');
  });

  it('refuse une preuve non organisationnelle', async () => {
    const user = userEvent.setup();
    const spaceToken = makeToken({
      subject_type: 'HUMAN',
      takibo_scope_level: 'SPACE',
      org_id: 'org-uuid',
      account_id: 'acc-uuid',
      space_id: 's-uuid',
    });
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ...OK_RESPONSE, accessToken: spaceToken }),
    });
    renderAt('/login');

    await fillCredentials(user);
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    // Le shell ne s'est PAS ouvert : l'identité n'apparaît nulle part.
    expect(screen.queryByText('john.doe@acme.com')).not.toBeInTheDocument();
  });

  it('redirige /app/** vers /login sans session', async () => {
    const router = renderAt('/app/dashboard');

    expect(
      await screen.findByRole('heading', { name: 'Bienvenue dans TAKIBO' }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('déconnecte et revient à /login', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(okFetch());
    const router = renderAt('/login');

    await fillCredentials(user);
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));
    await screen.findAllByText('john.doe@acme.com');

    await user.click(screen.getByRole('button', { name: 'Se déconnecter' }));

    expect(
      await screen.findByRole('heading', { name: 'Bienvenue dans TAKIBO' }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });
});
