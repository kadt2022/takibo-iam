import { cn } from '@/shared/utilities/cn';

interface LogoProps {
  /** Hauteur du logo en pixels. */
  size?: number;
  withWordmark?: boolean;
  className?: string;
}

/** Logo TAKIBO : lockup complet avec wordmark, ou symbole seul. */
export function Logo({ size = 40, withWordmark = true, className }: LogoProps) {
  const width = withWordmark ? Math.round(size * 3.42) : size;

  return (
    <span className={cn('inline-flex items-center', className)}>
      <img
        src={withWordmark ? '/takibo-logo.png' : '/favicon.svg'}
        alt="TAKIBO"
        width={width}
        height={size}
        className={cn(
          'block shrink-0 object-center',
          withWordmark ? 'object-cover' : 'object-contain',
        )}
        style={{ width, height: size }}
      />
    </span>
  );
}
