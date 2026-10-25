import type { OrganizationDashboardSummary } from '@/features/dashboard/model/summary';
import { authGet } from '@/shared/api/http';

/**
 * GET /api/v1/orgs/{organizationId}/dashboard/summary → compteurs réels de
 * l'organisation. Le segment est l'UUID {@code organizationId}, jamais l'orgCode.
 * Surface réservée à l'autorité ORG côté backend : un 403 masque la surface.
 */
export function fetchOrganizationDashboardSummary(
  token: string,
  organizationId: string,
): Promise<OrganizationDashboardSummary> {
  return authGet<OrganizationDashboardSummary>(
    `/api/v1/orgs/${organizationId}/dashboard/summary`,
    token,
  );
}
