import { Layers } from 'lucide-react';

import { spaceUnavailabilityReason } from '@/features/context-selector/model/context';
import { SpaceStatusPill } from '@/features/organization/components/SpaceStatusPill';
import type { AccessibleSpace } from '@/features/spaces/model/space';
import { cn } from '@/shared/utilities/cn';

interface SpaceContextOptionProps {
  space: AccessibleSpace;
  onSelect: (spaceId: string) => void;
}

/**
 * Une option Space du sélecteur : nom, code, statut réel. `selectable = false`
 * reste VISIBLE mais désactivée (aria-disabled, pas de retrait du DOM), avec la
 * raison d'indisponibilité dérivée des données du contrat — les technologies
 * d'assistance lisent le Space et sa raison.
 */
export function SpaceContextOption({ space, onSelect }: SpaceContextOptionProps) {
  const disabled = !space.selectable;

  return (
    <button
      type="button"
      role="menuitem"
      aria-disabled={disabled || undefined}
      onClick={() => {
        if (!disabled) {
          onSelect(space.spaceId);
        }
      }}
      className={cn(
        'flex w-full items-center gap-3 rounded-md px-2.5 py-2 text-left text-sm',
        'focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary',
        disabled
          ? 'cursor-not-allowed opacity-60'
          : 'transition-colors duration-150 hover:bg-primary/10',
      )}
    >
      {/* Colonne de la coche (vide : aucun Space n'est le contexte actif en UI 06A). */}
      <span className="size-4 shrink-0" aria-hidden="true" />
      <span className="grid size-7 shrink-0 place-items-center rounded-md bg-primary/12 text-primary">
        <Layers className="size-3.5" aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1 leading-tight">
        <span className="block truncate font-medium text-text">{space.name}</span>
        <span className="block truncate text-xs text-text-muted">
          {space.code}
          {disabled && ` — Indisponible · ${spaceUnavailabilityReason(space)}`}
        </span>
      </span>
      <SpaceStatusPill status={space.spaceStatus} />
    </button>
  );
}
