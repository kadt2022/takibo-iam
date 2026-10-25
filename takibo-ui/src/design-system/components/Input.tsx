import type { ComponentPropsWithRef } from 'react';

import { cn } from '@/shared/utilities/cn';

type InputProps = ComponentPropsWithRef<'input'>;

export function Input({ className, ...props }: InputProps) {
  return (
    <input
      className={cn(
        'h-11 w-full rounded-md border border-border bg-surface px-3.5 text-sm text-text',
        'placeholder:text-text-muted/60',
        'transition-colors duration-150',
        'hover:border-text-muted/50',
        'focus-visible:border-focus focus-visible:outline-none focus-visible:shadow-focus',
        'aria-invalid:border-danger aria-invalid:focus-visible:shadow-none',
        className,
      )}
      {...props}
    />
  );
}
