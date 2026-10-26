import { useQuery } from '@tanstack/react-query';

import { fetchMySpaces } from '@/features/spaces/api/spaces-api';
import { useSession } from '@/shared/security/session-context';

/** Charge GET /api/v1/me/spaces avec le token ORGANIZATION en session. */
export function useMySpaces() {
  const { session } = useSession();
  const token = session?.accessToken;

  return useQuery({
    queryKey: ['me-spaces'],
    queryFn: () => fetchMySpaces(token as string),
    enabled: Boolean(token),
  });
}
