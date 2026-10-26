import type { LoginCredentials, LoginSession } from '@/features/authentication/model/login';

/**
 * Erreur de connexion portant un message destiné à l'utilisateur.
 * IAM 31 : le backend est volontairement muet (401 uniforme, cause réelle en
 * audit) — l'UI relaie cette uniformité sans inventer de détail.
 */
export class LoginError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'LoginError';
  }
}

const UNIFORM_FAILURE_MESSAGE = 'Impossible de valider cette connexion.';

/**
 * Login humain organisationnel contre TIS-CORE (récit IAM 31 / UI 01.6) :
 * orgCode + email + password → token de portée ORGANIZATION.
 * En dev, /api est relayé vers takibo-iam-boot par le proxy Vite.
 *
 * Règles de sécurité : les identifiants ne sont jamais journalisés, jamais
 * placés dans une URL ; le token rendu ne doit jamais être persisté dans le
 * navigateur (session en mémoire uniquement, jusqu'au BFF du récit UI 02).
 */
export async function authenticate(credentials: LoginCredentials): Promise<LoginSession> {
  let response: Response;
  try {
    response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(credentials),
    });
  } catch {
    throw new LoginError('Le service TAKIBO est injoignable. Vérifiez que le backend est démarré.');
  }

  if (!response.ok) {
    throw new LoginError(
      response.status === 401 || response.status === 400
        ? UNIFORM_FAILURE_MESSAGE
        : 'Connexion impossible pour le moment. Veuillez réessayer.',
    );
  }

  return (await response.json()) as LoginSession;
}
