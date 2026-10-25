import { FlaskConical } from 'lucide-react';

import { cn } from '@/shared/utilities/cn';

interface DemoTagProps {
  className?: string;
  label?: string;
}

/**
 * Marqueur explicite « donnée non branchée ». Récit UI 02 : l'identité et la
 * session sont réelles, mais les indicateurs, graphiques et listes du tableau de
 * bord restent des démonstrations jusqu'aux récits UI 03+. Chaque surface encore
 * simulée le dit avec ce badge, plutôt qu'un badge global trompeur.
 */
export function DemoTag({ className, label = 'Démonstration' }: DemoTagProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border border-warning/40 bg-warning/10 px-2.5 py-1 text-[11px] font-medium text-warning',
        className,
      )}
      title="Donnée de démonstration — branchement réel aux récits UI 03 et suivants."
    >
      <FlaskConical className="size-3.5" aria-hidden="true" />
      {label}
    </span>
  );
}
