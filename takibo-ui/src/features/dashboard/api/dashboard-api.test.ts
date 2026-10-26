import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchOrganizationDashboardSummary } from '@/features/dashboard/api/dashboard-api';
import { ApiError, ApiForbiddenError } from '@/shared/api/http';

const fetchMock = vi.fn();

function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

const SUMMARY = {
  organizationId: 'org-uuid',
  usersTotal: 2,
  activeUsersTotal: 2,
  spacesTotal: 1,
  oauthClientsTotal: 1,
  generatedAt: '2026-07-16T10:00:00Z',
};

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('fetchOrganizationDashboardSummary', () => {
  it('cible l’UUID organizationId et envoie le bearer token', async () => {
    fetchMock.mockResolvedValue(jsonResponse(SUMMARY));

    const summary = await fetchOrganizationDashboardSummary('tok', 'org-uuid');

    expect(summary.usersTotal).toBe(2);
    expect(summary.activeUsersTotal).toBe(2);
    expect(summary.spacesTotal).toBe(1);
    expect(summary.oauthClientsTotal).toBe(1);

    const call = fetchMock.mock.calls[0]!;
    expect(call[0]).toBe('/api/v1/orgs/org-uuid/dashboard/summary');
    expect((call[1] as RequestInit).headers).toMatchObject({ Authorization: 'Bearer tok' });
  });

  it('mappe un 403 en ApiForbiddenError (surface masquée)', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}, 403));
    await expect(fetchOrganizationDashboardSummary('tok', 'org-uuid')).rejects.toBeInstanceOf(
      ApiForbiddenError,
    );
  });

  it('mappe un 500 en ApiError', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}, 500));
    await expect(fetchOrganizationDashboardSummary('tok', 'org-uuid')).rejects.toBeInstanceOf(
      ApiError,
    );
  });
});
