import type { ComponentPropsWithRef } from 'react';

import { cn } from '@/shared/utilities/cn';

type CardProps = ComponentPropsWithRef<'div'>;

export function Card({ className, ...props }: CardProps) {
  return (
    <div
      className={cn(
        'rounded-lg border border-border/70 bg-surface-elevated/80 shadow-card backdrop-blur-sm',
        className,
      )}
      {...props}
    />
  );
}
