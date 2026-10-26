import { useQuery } from '@tanstack/react-query';

import { fetchOrganizationDashboardSummary } from '@/features/dashboard/api/dashboard-api';
import { ApiForbiddenError } from '@/shared/api/http';
import { useSession } from '@/shared/security/session-context';

/**
 * Charge le résumé réel de l'organisation (compteurs users/spaces).
 * `organizationId` = UUID de la session (jamais l'orgCode). Un 403 n'est jamais
 * retenté : la surface se masque, elle ne « réessaie » pas une frontière.
 */
export function useOrgDashboardSummary(options: { enabled?: boolean } = {}) {
  const { session } = useSession();
  const token = session?.accessToken;
  const organizationId = session?.organizationId;

  return useQuery({
    queryKey: ['org-dashboard-summary', organizationId],
    queryFn: () => fetchOrganizationDashboardSummary(token as string, organizationId as string),
    enabled: Boolean(token) && Boolean(organizationId) && (options.enabled ?? true),
    retry: (failureCount, error) => !(error instanceof ApiForbiddenError) && failureCount < 1,
  });
}
