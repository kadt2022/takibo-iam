/**
 * Appels HTTP authentifiés par la preuve de la session (token en mémoire).
 * Couture unique : toute surface backend passe par ici.
 */

/**
 * Refus de frontière (HTTP 403) : le contexte du token ne permet pas cette
 * surface. Distinct d'une erreur technique — l'UI masque la surface plutôt que
 * d'afficher une erreur globale, et ne réessaie jamais une frontière.
 */
export class ApiForbiddenError extends Error {
  constructor(message = 'Accès refusé à cette surface.') {
    super(message);
    this.name = 'ApiForbiddenError';
  }
}

/**
 * Preuve refusée (HTTP 401) : le token n'est plus accepté. La session en
 * mémoire ne vaut plus rien — la politique est de la fermer et de revenir au
 * login, jamais de réessayer avec la même preuve.
 */
export class ApiUnauthorizedError extends Error {
  constructor(message = 'Session expirée ou invalide.') {
    super(message);
    this.name = 'ApiUnauthorizedError';
  }
}

/** Échec technique récupérable (réseau, 5xx, réponse illisible). */
export class ApiError extends Error {
  constructor(message = 'Chargement impossible pour le moment.') {
    super(message);
    this.name = 'ApiError';
  }
}

export async function authGet<T>(path: string, token: string): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    throw new ApiError('Le service TAKIBO est injoignable.');
  }

  if (response.status === 401) {
    throw new ApiUnauthorizedError();
  }
  if (response.status === 403) {
    throw new ApiForbiddenError();
  }
  if (!response.ok) {
    throw new ApiError();
  }

  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError();
  }
}
