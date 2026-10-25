import type { ComponentPropsWithRef, ReactNode } from 'react';

import { Spinner } from '@/design-system/components/Spinner';
import { cn } from '@/shared/utilities/cn';

type ButtonVariant = 'primary' | 'ghost';

interface ButtonProps extends ComponentPropsWithRef<'button'> {
  variant?: ButtonVariant;
  /** Affiche un indicateur de chargement et bloque les soumissions multiples. */
  isLoading?: boolean;
  loadingLabel?: ReactNode;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-primary text-on-primary hover:bg-primary-hover active:translate-y-px ' +
    'disabled:hover:bg-primary',
  ghost: 'bg-transparent text-text-muted hover:text-text',
};

export function Button({
  variant = 'primary',
  isLoading = false,
  loadingLabel,
  disabled,
  className,
  children,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
      className={cn(
        'inline-flex h-11 items-center justify-center gap-2 rounded-md px-5',
        'text-sm font-semibold transition-colors duration-150',
        'disabled:cursor-not-allowed disabled:opacity-70',
        variantClasses[variant],
        className,
      )}
      {...props}
    >
      {isLoading ? (
        <>
          <Spinner />
          {loadingLabel ?? children}
        </>
      ) : (
        children
      )}
    </button>
  );
}
