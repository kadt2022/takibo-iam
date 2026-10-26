import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchMySpaces, fetchOrganizationSpaces } from '@/features/spaces/api/spaces-api';
import { ApiError, ApiForbiddenError } from '@/shared/api/http';

const fetchMock = vi.fn();

function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('fetchMySpaces', () => {
  it('déballe les items de l’enveloppe et envoie le bearer token', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        organizationId: 'org-uuid',
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
        ],
      }),
    );

    const spaces = await fetchMySpaces('tok');

    expect(spaces).toHaveLength(1);
    expect(spaces[0]!.code).toBe('finance');
    const call = fetchMock.mock.calls[0]!;
    expect(call[0]).toBe('/api/v1/me/spaces');
    expect((call[1] as RequestInit).headers).toMatchObject({ Authorization: 'Bearer tok' });
  });

  it('mappe un 403 en ApiForbiddenError', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}, 403));
    await expect(fetchMySpaces('tok')).rejects.toBeInstanceOf(ApiForbiddenError);
  });

  it('mappe une erreur réseau en ApiError', async () => {
    fetchMock.mockRejectedValue(new Error('down'));
    await expect(fetchMySpaces('tok')).rejects.toBeInstanceOf(ApiError);
  });
});

describe('fetchOrganizationSpaces', () => {
  it('cible l’UUID organizationId avec page/size/status/search', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    );

    await fetchOrganizationSpaces('tok', 'org-uuid', {
      page: 2,
      size: 20,
      status: 'ACTIVE',
      search: 'fin',
    });

    const url = fetchMock.mock.calls[0]![0] as string;
    expect(url).toContain('/api/v1/orgs/org-uuid/spaces?');
    expect(url).toContain('page=2');
    expect(url).toContain('size=20');
    expect(url).toContain('status=ACTIVE');
    expect(url).toContain('search=fin');
  });

  it('omet status et search vides', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    );

    await fetchOrganizationSpaces('tok', 'org-uuid', { page: 0, size: 20 });

    const url = fetchMock.mock.calls[0]![0] as string;
    expect(url).not.toContain('status=');
    expect(url).not.toContain('search=');
  });

  it('mappe un 403 en ApiForbiddenError (surface masquée)', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}, 403));
    await expect(fetchOrganizationSpaces('tok', 'org-uuid')).rejects.toBeInstanceOf(
      ApiForbiddenError,
    );
  });

  it('mappe un 500 en ApiError', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}, 500));
    await expect(fetchOrganizationSpaces('tok', 'org-uuid')).rejects.toBeInstanceOf(ApiError);
  });
});
