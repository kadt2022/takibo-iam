import { useEffect } from 'react';

import { useQuery } from '@tanstack/react-query';

import { fetchMySpaces } from '@/features/spaces/api/spaces-api';
import { ApiForbiddenError, ApiUnauthorizedError } from '@/shared/api/http';
import { useSession } from '@/shared/security/session-context';

/**
 * Spaces accessibles du compte courant pour le sélecteur de contexte —
 * GET /api/v1/me/spaces avec le token ORGANIZATION en mémoire.
 *
 * Politique de preuve :
 *  - 401 : la preuve ne vaut plus rien → fermeture de session (la garde de
 *    session ramène au login) ;
 *  - 403 : frontière — état d'accès indisponible, jamais de déconnexion
 *    automatique ni de réessai ;
 *  - erreur technique : un seul réessai automatique, puis réessai manuel.
 */
export function useAccessibleSpaces(options: { enabled?: boolean } = {}) {
  const { session, closeSession } = useSession();
  const token = session?.accessToken;

  const query = useQuery({
    // Isole les données entre deux connexions sans exposer le bearer token
    // dans la clé de cache (visible dans les outils React Query).
    queryKey: [
      'context-selector-spaces',
      session?.organizationId,
      session?.accountId,
      session?.expiresAt,
    ],
    queryFn: () => fetchMySpaces(token as string),
    enabled: Boolean(token) && (options.enabled ?? true),
    retry: (failureCount, error) =>
      !(error instanceof ApiForbiddenError) &&
      !(error instanceof ApiUnauthorizedError) &&
      failureCount < 1,
  });

  const unauthorized = query.error instanceof ApiUnauthorizedError;
  useEffect(() => {
    if (unauthorized) {
      closeSession();
    }
  }, [unauthorized, closeSession]);

  return query;
}
