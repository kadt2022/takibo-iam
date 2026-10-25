import { cn } from '@/shared/utilities/cn';

interface BoundaryMotifProps {
  className?: string;
}

/**
 * Motif de fond TAKIBO : des frontières concentriques rayonnant depuis un
 * foyer. Chaque anneau est ouvert — une frontière réelle n'est pas un mur,
 * c'est une limite que seul un accès situé peut traverser.
 */
export function BoundaryMotif({ className }: BoundaryMotifProps) {
  const rings = [90, 150, 215, 285, 360, 440];

  return (
    <svg
      viewBox="0 0 800 800"
      fill="none"
      aria-hidden="true"
      className={cn('pointer-events-none select-none', className)}
    >
      {rings.map((radius, index) => (
        <circle
          key={radius}
          cx="400"
          cy="400"
          r={radius}
          stroke="currentColor"
          strokeWidth="1.25"
          strokeDasharray={`${radius * 4.2} ${radius * 2.1}`}
          strokeDashoffset={radius * index * 0.7}
          strokeLinecap="round"
          opacity={1 - index * 0.13}
        />
      ))}
      <circle cx="400" cy="400" r="34" fill="var(--tk-color-primary)" opacity="0.9" />
      <circle
        cx="400"
        cy="400"
        r="60"
        stroke="var(--tk-color-primary)"
        strokeWidth="1.5"
        opacity="0.45"
      />
    </svg>
  );
}
