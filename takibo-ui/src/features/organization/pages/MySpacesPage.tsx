import { Layers } from 'lucide-react';

import { Card } from '@/design-system/components/Card';
import { EmptyState } from '@/design-system/components/EmptyState';
import { ErrorState } from '@/design-system/components/ErrorState';
import { Loading } from '@/design-system/components/Loading';
import { SpaceStatusPill } from '@/features/organization/components/SpaceStatusPill';
import { useMySpaces } from '@/features/spaces/hooks/use-my-spaces';
import { userStatusLabel } from '@/features/spaces/model/space';

/**
 * « Mes Spaces » (récit UI 03) — GET /api/v1/me/spaces.
 * Les Spaces où le compte courant possède un profil local. Le rôle local n'est
 * pas exposé par ce contrat : on ne l'invente pas. L'ouverture réelle d'un
 * contexte de Space (échange ORG → SPACE) arrive à un récit ultérieur ; ici le
 * bouton « Ouvrir » ne simule aucun token de Space.
 */
export function MySpacesPage() {
  const { data, isPending, isError, refetch } = useMySpaces();
  const spaces = data ?? [];

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-bold text-text">Mes Spaces</h1>
        <p className="mt-1 text-sm text-text-muted">
          Les espaces auxquels votre compte appartient dans cette organisation.
        </p>
      </header>

      {isPending ? (
        <Loading label="Chargement de vos Spaces…" />
      ) : isError ? (
        <ErrorState
          description="Impossible de charger vos Spaces pour le moment."
          onRetry={() => refetch()}
        />
      ) : spaces.length === 0 ? (
        <EmptyState
          icon={Layers}
          title="Aucun Space accessible"
          description="Votre compte n’a de profil dans aucun Space de cette organisation."
        />
      ) : (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-text-muted">
                  <th className="px-5 py-3 font-medium">Space</th>
                  <th className="px-5 py-3 font-medium">Statut du Space</th>
                  <th className="px-5 py-3 font-medium">Mon profil</th>
                  <th className="px-5 py-3 text-right font-medium">Accès</th>
                </tr>
              </thead>
              <tbody>
                {spaces.map((space) => (
                  <tr key={space.spaceId} className="border-b border-border last:border-none">
                    <td className="px-5 py-3.5">
                      <span className="block font-medium text-text">{space.name}</span>
                      <span className="block font-mono text-xs text-text-muted">{space.code}</span>
                    </td>
                    <td className="px-5 py-3.5">
                      <SpaceStatusPill status={space.spaceStatus} />
                    </td>
                    <td className="px-5 py-3.5 text-text-muted">
                      {userStatusLabel(space.userStatus)}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      {space.selectable ? (
                        <button
                          type="button"
                          disabled
                          className="rounded-md border border-primary/40 px-3 py-1.5 text-xs font-medium text-primary transition-colors hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:bg-transparent"
                          title="L'ouverture d'un contexte de Space (échange ORG → SPACE) arrive à un récit ultérieur."
                        >
                          Ouvrir
                        </button>
                      ) : (
                        <span className="text-xs text-text-muted">Indisponible</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <p className="text-xs text-text-muted">
        L’ouverture d’un Space établira un <strong className="text-text">contexte situé</strong>{' '}
        (jeton de Space) — cette étape arrivera après l’échange ORG → SPACE.
      </p>
    </div>
  );
}
