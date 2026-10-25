import type { ComponentType, ReactNode } from 'react';

import { cn } from '@/shared/utilities/cn';

interface EmptyStateProps {
  icon?: ComponentType<{ className?: string; 'aria-hidden'?: boolean | 'true' | 'false' }>;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}

/**
 * État vide structuré : une surface qui n'a pas encore de contenu, présentée
 * proprement plutôt que laissée blanche. Sert aussi de « bientôt » honnête
 * pour les fonctionnalités des récits à venir.
 */
export function EmptyState({ icon: Icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-border',
        'bg-surface/40 px-6 py-14 text-center',
        className,
      )}
    >
      {Icon && (
        <span className="grid size-11 place-items-center rounded-full border border-border bg-surface text-text-muted">
          <Icon className="size-5" aria-hidden="true" />
        </span>
      )}
      <h3 className="text-base font-semibold text-text">{title}</h3>
      {description && <p className="max-w-sm text-sm text-text-muted">{description}</p>}
      {action && <div className="mt-1">{action}</div>}
    </div>
  );
}
