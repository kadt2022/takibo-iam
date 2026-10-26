import { ChevronLeft, ChevronRight, Plus, Search } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';

import { Button } from '@/design-system/components/Button';
import { Card } from '@/design-system/components/Card';
import { EmptyState } from '@/design-system/components/EmptyState';
import { ErrorState } from '@/design-system/components/ErrorState';
import { Forbidden } from '@/design-system/components/Forbidden';
import { Loading } from '@/design-system/components/Loading';
import { SpaceStatusPill } from '@/features/organization/components/SpaceStatusPill';
import { useOrganizationSpaces } from '@/features/spaces/hooks/use-organization-spaces';
import type { SpaceStatus } from '@/features/spaces/model/space';
import { ApiForbiddenError } from '@/shared/api/http';
import { useIdentity } from '@/shared/identity/useIdentity';
import { isOrgAdmin } from '@/shared/identity/roles';

const PAGE_SIZE = 20;

const STATUS_OPTIONS: { value: '' | SpaceStatus; label: string }[] = [
  { value: '', label: 'Tous les statuts' },
  { value: 'ACTIVE', label: 'Actif' },
  { value: 'SUSPENDED', label: 'Suspendu' },
  { value: 'CREATING', label: 'En création' },
  { value: 'DISABLED', label: 'Désactivé' },
];

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('fr-FR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

/**
 * « Gestion des Spaces » (récit UI 03) — GET /api/v1/orgs/{organizationId}/spaces.
 * Inventaire administratif de TOUTE l'organisation, réservé à l'autorité ORG
 * (R_ORG_OWNER / R_ORG_ADMIN). Route par UUID `organizationId` (jamais l'orgCode).
 * Un 403 masque la surface — il n'affiche pas une erreur globale.
 */
export function SpacesManagementPage() {
  const { roles } = useIdentity();
  const admin = isOrgAdmin(roles);

  const [status, setStatus] = useState<'' | SpaceStatus>('');
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  const query = {
    page,
    size: PAGE_SIZE,
    status: status || undefined,
    search: search || undefined,
  };
  const { data, isPending, isError, error, refetch } = useOrganizationSpaces(query, {
    enabled: admin,
  });

  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    setSearch(searchInput.trim());
    setPage(0);
  };

  const header = (
    <header className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-2xl font-bold text-text">Gestion des Spaces</h1>
        <p className="mt-1 text-sm text-text-muted">
          Tous les espaces de l’organisation — vue réservée à l’autorité d’organisation.
        </p>
      </div>
      <Button disabled title="La création réelle de Space sera branchée à un récit ultérieur.">
        <Plus className="size-4" aria-hidden="true" />
        Créer un Space
      </Button>
    </header>
  );

  // Frontière : sans autorité ORG, la surface est masquée (pas d'appel réseau).
  if (!admin) {
    return (
      <div className="flex flex-col gap-6">
        {header}
        <Forbidden description="La gestion des Spaces est réservée aux administrateurs de l’organisation." />
      </div>
    );
  }

  const spaces = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <div className="flex flex-col gap-6">
      {header}

      <div className="flex flex-wrap items-center gap-3">
        <form onSubmit={submitSearch} className="flex items-center gap-2">
          <div className="flex h-11 items-center gap-2 rounded-md border border-border bg-surface px-3 text-sm">
            <Search className="size-4 text-text-muted" aria-hidden="true" />
            <input
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Rechercher un Space…"
              aria-label="Rechercher un Space"
              className="w-48 bg-transparent text-text placeholder:text-text-muted/60 focus:outline-none"
            />
          </div>
          <Button type="submit" variant="ghost" className="border border-border">
            Rechercher
          </Button>
        </form>

        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as '' | SpaceStatus);
            setPage(0);
          }}
          aria-label="Filtrer par statut"
          className="h-11 rounded-md border border-border bg-surface px-3 text-sm text-text focus-visible:border-focus focus-visible:outline-none"
        >
          {STATUS_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      {isPending ? (
        <Loading label="Chargement des Spaces de l’organisation…" />
      ) : isError ? (
        error instanceof ApiForbiddenError ? (
          <Forbidden description="Votre contexte ne permet pas de lister les Spaces de cette organisation." />
        ) : (
          <ErrorState
            description="Impossible de charger l’inventaire des Spaces pour le moment."
            onRetry={() => refetch()}
          />
        )
      ) : spaces.length === 0 ? (
        <EmptyState
          title="Aucun Space"
          description={
            search || status
              ? 'Aucun Space ne correspond à ces critères.'
              : 'Cette organisation n’a encore aucun Space.'
          }
        />
      ) : (
        <>
          <Card className="overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-text-muted">
                    <th className="px-5 py-3 font-medium">Space</th>
                    <th className="px-5 py-3 font-medium">Code</th>
                    <th className="px-5 py-3 font-medium">Statut</th>
                    <th className="px-5 py-3 font-medium">Propriétaire</th>
                    <th className="px-5 py-3 font-medium">Créé</th>
                    <th className="px-5 py-3 font-medium">Modifié</th>
                  </tr>
                </thead>
                <tbody>
                  {spaces.map((space) => (
                    <tr key={space.id} className="border-b border-border last:border-none">
                      <td className="px-5 py-3.5 font-medium text-text">{space.name}</td>
                      <td className="px-5 py-3.5 font-mono text-xs text-text-muted">
                        {space.code}
                      </td>
                      <td className="px-5 py-3.5">
                        <SpaceStatusPill status={space.status} />
                      </td>
                      <td
                        className="px-5 py-3.5 font-mono text-xs text-text-muted"
                        title={space.ownerAccountId}
                      >
                        {space.ownerAccountId.slice(0, 8)}…
                      </td>
                      <td className="px-5 py-3.5 text-text-muted">{formatDate(space.createdAt)}</td>
                      <td className="px-5 py-3.5 text-text-muted">{formatDate(space.updatedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-text-muted">
            <span>
              {totalElements} Space{totalElements > 1 ? 's' : ''} · page {page + 1} sur{' '}
              {Math.max(totalPages, 1)}
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                className="border border-border"
                disabled={page <= 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                <ChevronLeft className="size-4" aria-hidden="true" />
                Précédent
              </Button>
              <Button
                variant="ghost"
                className="border border-border"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((current) => current + 1)}
              >
                Suivant
                <ChevronRight className="size-4" aria-hidden="true" />
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
