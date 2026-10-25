import { Spinner } from '@/design-system/components/Spinner';
import { cn } from '@/shared/utilities/cn';

interface LoadingProps {
  label?: string;
  className?: string;
}

/** État de chargement neutre, réutilisable dans toute surface. */
export function Loading({ label = 'Chargement…', className }: LoadingProps) {
  return (
    <div
      role="status"
      className={cn(
        'flex items-center justify-center gap-3 py-12 text-sm text-text-muted',
        className,
      )}
    >
      <Spinner />
      {label}
    </div>
  );
}
