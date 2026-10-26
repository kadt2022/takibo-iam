import { Building2, ChevronDown } from 'lucide-react';
import type { RefObject } from 'react';

import { cn } from '@/shared/utilities/cn';

interface ContextSelectorTriggerProps {
  orgCode: string;
  organizationId: string;
  open: boolean;
  collapsed: boolean;
  onToggle: () => void;
  buttonRef: RefObject<HTMLButtonElement | null>;
}

/**
 * Bouton du sélecteur de contexte — toute la carte Organisation est cliquable,
 * avec un unique chevron à droite qui pivote à l'ouverture (façon GitHub).
 */
export function ContextSelectorTrigger({
  orgCode,
  organizationId,
  open,
  collapsed,
  onToggle,
  buttonRef,
}: ContextSelectorTriggerProps) {
  return (
    <button
      ref={buttonRef}
      type="button"
      onClick={onToggle}
      aria-haspopup="menu"
      aria-expanded={open}
      title={`Organisation ${orgCode} · ${organizationId}`}
      className={cn(
        'flex w-full items-center gap-3 rounded-md border border-border bg-background/40 p-2.5 text-left',
        'transition-colors duration-150 hover:border-primary/50 hover:bg-primary/5',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        open && 'border-primary/50 bg-primary/5',
        collapsed && 'lg:justify-center',
      )}
    >
      <span className="grid size-8 shrink-0 place-items-center rounded-md bg-primary/12 text-primary">
        <Building2 className="size-4" aria-hidden="true" />
      </span>
      <span className={cn('min-w-0 flex-1 leading-tight', collapsed && 'lg:hidden')}>
        <span className="block truncate text-sm font-semibold text-text">{orgCode}</span>
        <span className="block truncate text-xs text-text-muted">Organisation</span>
      </span>
      <ChevronDown
        className={cn(
          'size-4 shrink-0 text-text-muted transition-transform duration-150',
          open && 'rotate-180',
          collapsed && 'lg:hidden',
        )}
        aria-hidden="true"
      />
    </button>
  );
}
