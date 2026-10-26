import { TriangleAlert } from 'lucide-react';
import type { ReactNode } from 'react';

import { Button } from '@/design-system/components/Button';
import { EmptyState } from '@/design-system/components/EmptyState';

interface ErrorStateProps {
  title?: string;
  description?: ReactNode;
  onRetry?: () => void;
}

/** Échec récupérable, avec une action de reprise quand elle a du sens. */
export function ErrorState({
  title = 'Une erreur est survenue',
  description = 'Impossible de charger cette surface pour le moment.',
  onRetry,
}: ErrorStateProps) {
  return (
    <EmptyState
      icon={TriangleAlert}
      title={title}
      description={description}
      action={
        onRetry ? (
          <Button variant="ghost" onClick={onRetry} className="border border-border">
            Réessayer
          </Button>
        ) : undefined
      }
    />
  );
}
