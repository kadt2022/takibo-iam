import { useState } from 'react';
import type { RefObject } from 'react';

import { Info, RotateCcw, ShieldAlert } from 'lucide-react';
import type { UseQueryResult } from '@tanstack/react-query';

import { SpaceContextOption } from '@/features/context-selector/components/SpaceContextOption';
import type { SpaceSelectionState } from '@/features/context-selector/model/context';
import type { AccessibleSpace } from '@/features/spaces/model/space';
import { Spinner } from '@/design-system/components/Spinner';
import { ApiForbiddenError } from '@/shared/api/http';
import { cn } from '@/shared/utilities/cn';

interface ContextSelectorMenuProps {
  spaces: UseQueryResult<AccessibleSpace[]>;
  spaceSelection: SpaceSelectionState;
  onSelectSpace: (spaceId: string) => void;
  menuRef: RefObject<HTMLDivElement | null>;
}

/**
 * Popover du sélecteur de contexte, façon sélecteur de repository GitHub :
 * flyout à droite de la carte sur desktop, au-dessus du contenu. En tête :
 * titre + recherche immédiatement visible ; puis UNIQUEMENT les Spaces de
 * GET /api/v1/me/spaces filtrés par la recherche — l'Organisation n'est pas
 * répétée ici, la carte-déclencheur affiche déjà le contexte courant.
 */
export function ContextSelectorMenu({
  spaces,
  spaceSelection,
  onSelectSpace,
  menuRef,
}: ContextSelectorMenuProps) {
  const [query, setQuery] = useState('');
  const forbidden = spaces.isError && spaces.error instanceof ApiForbiddenError;
  const items = spaces.data ?? [];
  const needle = query.trim().toLowerCase();
  const filtered = needle
    ? items.filter((space) => `${space.name} ${space.code}`.toLowerCase().includes(needle))
    : items;

  return (
    <div
      ref={menuRef}
      role="menu"
      aria-label="Changer de contexte"
      tabIndex={-1}
      className={cn(
        'absolute z-50 w-[360px] max-w-[calc(100vw-2rem)] rounded-lg border border-border bg-surface-elevated shadow-card',
        // Mobile/tablette : sous la carte. Desktop : décalé à DROITE de la
        // carte (flyout façon GitHub), par-dessus le contenu.
        'left-0 top-full mt-2',
        'lg:left-[calc(100%+0.5rem)] lg:top-0 lg:mt-0',
      )}
    >
      {/* En-tête : titre + recherche visible immédiatement. */}
      <div className="border-b border-border px-3 pb-2.5 pt-2.5">
        <p className="pb-2 text-xs font-semibold text-text">Changer de contexte</p>
        <input
          type="text"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Rechercher un Space..."
          aria-label="Rechercher un Space"
          className="h-9 w-full rounded-md border border-border bg-background/60 px-3 text-sm text-text placeholder:text-text-muted focus-visible:border-focus focus-visible:outline-none"
        />
      </div>

      {/* Uniquement les Spaces accessibles — pas de répétition du contexte
          Organisation courant, déjà porté par la carte-déclencheur. */}
      <div className="p-1.5">
        <p className="px-2.5 pb-1 pt-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-text-muted">
          Mes Spaces
        </p>
        {spaces.isPending ? (
          <div className="flex items-center gap-2 px-2.5 py-2 text-xs text-text-muted">
            <Spinner className="size-3.5" />
            Chargement des Spaces…
          </div>
        ) : forbidden ? (
          <div className="flex items-start gap-2 px-2.5 py-2 text-xs text-text-muted">
            <ShieldAlert className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
            <span>Accès aux Spaces indisponible pour ce contexte.</span>
          </div>
        ) : spaces.isError ? (
          <div className="flex flex-col gap-1.5 px-2.5 py-2">
            <p className="text-xs text-text-muted">Impossible de charger vos Spaces.</p>
            <button
              type="button"
              onClick={() => void spaces.refetch()}
              className="inline-flex w-fit items-center gap-1.5 rounded-md border border-border px-2 py-1 text-xs text-text transition-colors duration-150 hover:border-primary/50 hover:bg-primary/5"
            >
              <RotateCcw className="size-3" aria-hidden="true" />
              Réessayer
            </button>
          </div>
        ) : items.length === 0 ? (
          <p className="px-2.5 py-2 text-xs text-text-muted">
            Votre compte n’a de profil dans aucun Space.
          </p>
        ) : filtered.length === 0 ? (
          <p className="px-2.5 py-2 text-xs text-text-muted">
            Aucun Space ne correspond à « {query.trim()} ».
          </p>
        ) : (
          filtered.map((space) => (
            <SpaceContextOption key={space.spaceId} space={space} onSelect={onSelectSpace} />
          ))
        )}
      </div>

      {/* Refus honnête : le clic n'est JAMAIS présenté comme une transition
          réussie tant que l'échange sécurisé ORG → SPACE n'existe pas. */}
      {spaceSelection.status === 'unsupported' && (
        <p className="mx-2 mb-2 flex items-start gap-2 rounded-md border border-warning/40 bg-warning/10 px-2.5 py-2 text-xs text-warning">
          <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
          <span>
            L’ouverture sécurisée du Space sera disponible avec l’établissement du contexte Space.
          </span>
        </p>
      )}
    </div>
  );
}
