import type { DemoSegment } from '@/shared/demo/demo';

interface DonutChartProps {
  segments: DemoSegment[];
  centerValue: number | string;
  centerLabel: string;
  size?: number;
}

/**
 * Donut catégoriel (démonstration). Rendu SVG pur, couleurs de la palette
 * graphique — distinctes des tokens sémantiques.
 */
export function DonutChart({ segments, centerValue, centerLabel, size = 136 }: DonutChartProps) {
  const total = segments.reduce((sum, seg) => sum + seg.value, 0) || 1;
  const stroke = Math.max(14, Math.round(size * 0.12));
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const center = size / 2;
  const chartSegments = segments.map((seg, index) => {
    const dash = (seg.value / total) * circumference;
    const offset = segments
      .slice(0, index)
      .reduce((sum, previous) => sum + (previous.value / total) * circumference, 0);

    return { dash, offset, seg };
  });

  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-center">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="shrink-0">
        <g transform={`rotate(-90 ${center} ${center})`}>
          <circle
            cx={center}
            cy={center}
            r={radius}
            fill="none"
            stroke="var(--tk-color-border)"
            strokeWidth={stroke}
            opacity="0.4"
          />
          {chartSegments.map(({ dash, offset, seg }) => (
            <circle
              key={seg.label}
              cx={center}
              cy={center}
              r={radius}
              fill="none"
              stroke={seg.color}
              strokeWidth={stroke}
              strokeDasharray={`${dash} ${circumference - dash}`}
              strokeDashoffset={-offset}
            />
          ))}
        </g>
        <text
          x={center}
          y={center - 2}
          textAnchor="middle"
          className="fill-text font-mono text-xl font-semibold"
          style={{ fontVariantNumeric: 'tabular-nums' }}
        >
          {centerValue}
        </text>
        <text
          x={center}
          y={center + 18}
          textAnchor="middle"
          className="fill-text-muted text-[11px]"
        >
          {centerLabel}
        </text>
      </svg>

      <ul className="flex w-full flex-col gap-2">
        {segments.map((seg) => (
          <li key={seg.label} className="flex items-center gap-2 text-xs">
            <span className="size-2 shrink-0 rounded-full" style={{ backgroundColor: seg.color }} />
            <span className="flex-1 text-text-muted">{seg.label}</span>
            <span className="font-medium text-text">{seg.pct}%</span>
            <span className="w-8 text-right font-mono text-[11px] text-text-muted">
              ({seg.value})
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
