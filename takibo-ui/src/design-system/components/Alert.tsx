import { CircleAlert, Info, TriangleAlert } from 'lucide-react';
import type { ReactNode } from 'react';

import { cn } from '@/shared/utilities/cn';

type AlertVariant = 'danger' | 'warning' | 'info';

interface AlertProps {
  variant?: AlertVariant;
  className?: string;
  children: ReactNode;
}

const variantStyles: Record<AlertVariant, { container: string; icon: typeof Info }> = {
  danger: { container: 'border-danger/40 bg-danger/10 text-danger', icon: CircleAlert },
  warning: { container: 'border-warning/40 bg-warning/10 text-warning', icon: TriangleAlert },
  info: { container: 'border-border bg-surface-elevated text-text-muted', icon: Info },
};

export function Alert({ variant = 'info', className, children }: AlertProps) {
  const { container, icon: Icon } = variantStyles[variant];

  return (
    <div
      role={variant === 'danger' ? 'alert' : 'status'}
      className={cn(
        'flex items-start gap-3 rounded-md border px-4 py-3 text-sm leading-relaxed',
        container,
        className,
      )}
    >
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div>{children}</div>
    </div>
  );
}
