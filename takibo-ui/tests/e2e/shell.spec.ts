import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

/**
 * Récit UI 02 — connexion réelle et session organisationnelle.
 * L'API de login est interceptée (pas de backend en CI) pour piloter le flux
 * complet : formulaire → session ORGANIZATION → shell honnête → déconnexion.
 */

function makeToken(claims: Record<string, unknown>): string {
  const b64 = (obj: unknown) => Buffer.from(JSON.stringify(obj)).toString('base64url');
  return `${b64({ alg: 'none', typ: 'JWT' })}.${b64(claims)}.sig`;
}

const ORG_ID = '9eb7b8d5-79c8-4ac5-b4b8-b61ad332390f';
const ACCOUNT_ID = 'a1b2c3d4-0000-0000-0000-000000000000';

function loginResponse(roles: string[]) {
  return {
    accessToken: makeToken({
      subject_type: 'HUMAN',
      takibo_scope_level: 'ORGANIZATION',
      auth_method: 'PASSWORD',
      org_id: ORG_ID,
      account_id: ACCOUNT_ID,
      roles,
      groups: [],
      permissions: [],
    }),
    tokenType: 'Bearer',
    expiresIn: 3600,
    scopeLevel: 'ORGANIZATION',
    organizationId: ORG_ID,
    accountId: ACCOUNT_ID,
  };
}

const ME_SPACES = {
  organizationId: ORG_ID,
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

const ORG_SPACES = {
  content: [
    {
      id: 'sp1',
      orgId: ORG_ID,
      code: 'finance',
      name: 'Finance',
      status: 'ACTIVE',
      ownerAccountId: 'aaaaaaaa-1111-2222-3333-444444444444',
      createdAt: '2026-01-15T10:00:00Z',
      updatedAt: '2026-02-20T10:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

const DASHBOARD_SUMMARY = {
  organizationId: ORG_ID,
  usersTotal: 2,
  activeUsersTotal: 2,
  spacesTotal: 1,
  oauthClientsTotal: 1,
  generatedAt: '2026-07-16T10:00:00Z',
};

async function mockApi(page: Page, roles: string[]) {
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(loginResponse(roles)),
    }),
  );
  await page.route('**/api/v1/orgs/*/dashboard/summary', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(DASHBOARD_SUMMARY),
    }),
  );
  await page.route('**/api/v1/me/spaces', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ME_SPACES),
    }),
  );
  await page.route('**/api/v1/orgs/*/spaces*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ORG_SPACES),
    }),
  );
}

async function login(page: Page, roles: string[] = ['R_ORG_ADMIN']) {
  await mockApi(page, roles);
  await page.goto('/login');
  await page.getByLabel('Organisation', { exact: true }).fill('acme');
  await page.getByLabel('Adresse courriel', { exact: true }).fill('john.doe@acme.com');
  await page.getByLabel('Mot de passe', { exact: true }).fill('secret123');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/app\/dashboard$/);
}

test.describe('Session organisationnelle (UI 02)', () => {
  test('la racine mène au login, la connexion ouvre le shell sur l’identité réelle', async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Bienvenue dans TAKIBO' })).toBeVisible();

    await login(page);

    // TopBar minimale : expiration de session, identité réelle et déconnexion.
    await expect(page.getByText('john.doe@acme.com').first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible();
    await expect(page.getByText('Contexte actuel')).toHaveCount(0);
    await expect(page.getByText('Rechercher…')).toHaveCount(0);
    await expect(page.getByText(/Session jusqu’à/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Notifications' })).toHaveCount(0);
    await expect(page.getByRole('heading', { name: /Bienvenue/ })).toHaveCount(0);
  });

  test('protège /app/** : sans session, redirige vers /login', async ({ page }) => {
    await page.goto('/app/dashboard');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Bienvenue dans TAKIBO' })).toBeVisible();
  });

  test('navigue entre les onglets du contexte Organisation', async ({ page }) => {
    await login(page);

    await page.getByRole('link', { name: 'Mes Spaces', exact: true }).click();
    await expect(page).toHaveURL(/\/app\/my-spaces$/);
    await expect(page.getByRole('heading', { name: 'Mes Spaces' })).toBeVisible();
    await expect(page.getByText('Finance', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Ouvrir' }).first()).toBeDisabled();
    await expect(page.getByText('Indisponible')).toBeVisible();

    await page.getByRole('link', { name: 'Gestion des Spaces', exact: true }).click();
    await expect(page).toHaveURL(/\/app\/spaces$/);
    await expect(page.getByRole('button', { name: 'Créer un Space' })).toBeDisabled();

    await page.getByRole('link', { name: 'Paramètres', exact: true }).click();
    await expect(page).toHaveURL(/\/app\/settings$/);
  });

  test('la déconnexion ferme la session et revient au login', async ({ page }) => {
    await login(page);

    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Bienvenue dans TAKIBO' })).toBeVisible();

    // La session vit en mémoire : revenir en arrière ne rouvre pas le shell.
    await page.goto('/app/dashboard');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('le tiroir mobile reste complet même après un repli desktop', async ({ page }) => {
    await login(page);

    await page.getByRole('button', { name: 'Réduire le menu' }).click();
    await expect(page.getByRole('button', { name: 'Déployer le menu' })).toBeVisible();

    await page.setViewportSize({ width: 390, height: 800 });
    await page.getByRole('button', { name: 'Ouvrir le menu' }).click();

    await expect(page.getByRole('link', { name: 'Gestion des Spaces', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Réduire le menu' })).toHaveCount(0);
  });
});

test.describe('Spaces réels (UI 03)', () => {
  test('Org Admin voit l’inventaire réel dans Gestion des Spaces', async ({ page }) => {
    await login(page, ['R_ORG_ADMIN']);

    await page.getByRole('link', { name: 'Gestion des Spaces', exact: true }).click();
    await expect(page).toHaveURL(/\/app\/spaces$/);
    await expect(page.getByRole('heading', { name: 'Gestion des Spaces' })).toBeVisible();
    await expect(page.getByText('Finance', { exact: true })).toBeVisible();
    await expect(page.getByText(/1 Space ·/)).toBeVisible();
  });

  test('l’Org Admin voit les compteurs réels d’utilisateurs sur le tableau de bord', async ({
    page,
  }) => {
    await login(page, ['R_ORG_ADMIN']);

    await expect(page.getByText('Indicateurs réels')).toBeVisible();
    await expect(page.getByText('comptes distincts de l’organisation')).toBeVisible();
    // activeUsersTotal reste dans le contrat API mais n'est plus affiché.
    await expect(page.getByText('au moins un profil actif')).toHaveCount(0);
    // Clients OAuth2 est désormais un compteur réel du résumé, plus une démo.
    await expect(page.getByText('Clients OAuth2')).toHaveCount(1);
    // Plus aucune tuile KPI de démonstration sur le tableau de bord.
    await expect(page.getByText('vs période précédente')).toHaveCount(0);
    // Aucun lien n'est exposé avant que sa route réelle existe.
    await expect(page.getByRole('link', { name: 'Utilisateurs', exact: true })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Rôles', exact: true })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Groupes', exact: true })).toHaveCount(0);
  });

  test('un membre ne voit pas les menus réservés à l’autorité ORG', async ({ page }) => {
    await login(page, []);

    // Mes Spaces reste visible (appartenance) ; les surfaces admin sont masquées.
    await expect(page.getByRole('link', { name: 'Mes Spaces', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Gestion des Spaces', exact: true })).toHaveCount(
      0,
    );
    await expect(page.getByRole('link', { name: 'Utilisateurs', exact: true })).toHaveCount(0);
  });
});

test.describe('Sélecteur de contexte (UI 06A)', () => {
  test('ouvre le sélecteur, liste les Spaces réels et refuse un faux contexte Space', async ({
    page,
  }) => {
    await login(page);

    // Le bloc Organisation de la barre latérale est devenu un déclencheur.
    const selectorButton = page.getByRole('button', { name: 'acme Organisation' });
    await selectorButton.click();

    const menu = page.getByRole('menu', { name: 'Changer de contexte' });
    await expect(menu).toBeVisible();
    // Popover façon GitHub : titre + recherche visibles immédiatement.
    await expect(menu.getByText('Changer de contexte')).toBeVisible();
    await expect(menu.getByPlaceholder('Rechercher un Space...')).toBeVisible();
    // Le menu n'énumère QUE les Spaces : le contexte Organisation courant est
    // porté par la carte-déclencheur, il n'est pas répété dans la liste.
    await expect(menu.getByRole('menuitem', { name: /Organisation/ })).toHaveCount(0);
    // Spaces réels de /me/spaces : Finance disponible, Support suspendu désactivé.
    await expect(menu.getByRole('menuitem', { name: /Finance/ })).toBeVisible();
    const support = menu.getByRole('menuitem', { name: /Support/ });
    await expect(support).toHaveAttribute('aria-disabled', 'true');
    await expect(support).toContainText('Space suspendu');

    // Cliquer Finance n'ouvre AUCUN contexte Space : message sobre, shell inchangé.
    await menu.getByRole('menuitem', { name: /Finance/ }).click();
    await expect(
      page.getByText(
        'L’ouverture sécurisée du Space sera disponible avec l’établissement du contexte Space.',
      ),
    ).toBeVisible();
    await expect(page.getByText('john.doe@acme.com').first()).toBeVisible();

    // Escape ferme le menu ; le contexte reste Organisation.
    await page.keyboard.press('Escape');
    await expect(page.getByRole('menu', { name: 'Changer de contexte' })).toHaveCount(0);
    await expect(page).toHaveURL(/\/app\/dashboard$/);
  });
});
