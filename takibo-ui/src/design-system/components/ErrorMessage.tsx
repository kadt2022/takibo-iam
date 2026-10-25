import type { ReactNode } from 'react';

import { cn } from '@/shared/utilities/cn';

interface ErrorMessageProps {
  id?: string;
  className?: string;
  children?: ReactNode;
}

/**
 * Message d'erreur de champ. Toujours rendu (aria-live) afin que les
 * technologies d'assistance annoncent l'erreur dès son apparition.
 */
export function ErrorMessage({ id, className, children }: ErrorMessageProps) {
  return (
    <p id={id} aria-live="polite" className={cn('min-h-5 text-sm text-danger', className)}>
      {children}
    </p>
  );
}
