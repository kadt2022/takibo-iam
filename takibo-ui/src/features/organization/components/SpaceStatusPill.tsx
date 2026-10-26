import type { SpaceStatus } from '@/features/spaces/model/space';
import { cn } from '@/shared/utilities/cn';

const STATUS: Record<SpaceStatus, { label: string; className: string }> = {
  ACTIVE: { label: 'Actif', className: 'border-success/40 bg-success/10 text-success' },
  SUSPENDED: { label: 'Suspendu', className: 'border-warning/40 bg-warning/10 text-warning' },
  CREATING: { label: 'En création', className: 'border-primary/40 bg-primary/10 text-primary' },
  DISABLED: { label: 'Désactivé', className: 'border-danger/40 bg-danger/10 text-danger' },
};

export function SpaceStatusPill({ status }: { status: SpaceStatus }) {
  const { label, className } = STATUS[status];
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium',
        className,
      )}
    >
      <span className="size-1.5 rounded-full bg-current" />
      {label}
    </span>
  );
}
