import type {
  AccessibleSpace,
  CurrentUserSpacesResponse,
  OrganizationSpacePage,
} from '@/features/spaces/model/space';
import { authGet } from '@/shared/api/http';

/** GET /api/v1/me/spaces → les Spaces d'appartenance du compte courant. */
export async function fetchMySpaces(token: string): Promise<AccessibleSpace[]> {
  const response = await authGet<CurrentUserSpacesResponse>('/api/v1/me/spaces', token);
  return response.items;
}

export interface OrganizationSpacesQuery {
  page?: number;
  size?: number;
  status?: string;
  search?: string;
}

/**
 * GET /api/v1/orgs/{organizationId}/spaces → inventaire administratif.
 * Le segment est l'UUID {@code organizationId}, jamais l'orgCode lisible.
 */
export function fetchOrganizationSpaces(
  token: string,
  organizationId: string,
  query: OrganizationSpacesQuery = {},
): Promise<OrganizationSpacePage> {
  const params = new URLSearchParams();
  params.set('page', String(query.page ?? 0));
  params.set('size', String(query.size ?? 20));
  if (query.status) {
    params.set('status', query.status);
  }
  if (query.search && query.search.trim()) {
    params.set('search', query.search.trim());
  }
  return authGet<OrganizationSpacePage>(
    `/api/v1/orgs/${organizationId}/spaces?${params.toString()}`,
    token,
  );
}
