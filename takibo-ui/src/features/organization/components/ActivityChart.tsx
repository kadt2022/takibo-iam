import type { DemoSeries } from '@/shared/demo/demo';

interface ActivityChartProps {
  labels: string[];
  series: DemoSeries[];
  maxY: number;
}

const WIDTH = 560;
const HEIGHT = 232;
const PAD_LEFT = 34;
const PAD_RIGHT = 10;
const PAD_TOP = 10;
const PAD_BOTTOM = 26;

/**
 * Courbe d'activité multi-séries (démonstration). SVG responsive, grille
 * discrète, points marqués — même soin que la typographie.
 */
export function ActivityChart({ labels, series, maxY }: ActivityChartProps) {
  const innerW = WIDTH - PAD_LEFT - PAD_RIGHT;
  const innerH = HEIGHT - PAD_TOP - PAD_BOTTOM;
  const count = labels.length;

  const x = (index: number) => PAD_LEFT + (count <= 1 ? 0 : innerW * (index / (count - 1)));
  const y = (value: number) => PAD_TOP + innerH * (1 - value / maxY);

  const ticks = Array.from({ length: maxY / 100 + 1 }, (_, i) => i * 100);

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-wrap gap-x-5 gap-y-1">
        {series.map((serie) => (
          <li key={serie.name} className="flex items-center gap-2 text-xs text-text-muted">
            <span className="size-2.5 rounded-full" style={{ backgroundColor: serie.color }} />
            {serie.name}
          </li>
        ))}
      </ul>

      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        width="100%"
        role="img"
        aria-label="Activité de l’organisation"
      >
        {ticks.map((tick) => (
          <g key={tick}>
            <line
              x1={PAD_LEFT}
              y1={y(tick)}
              x2={WIDTH - PAD_RIGHT}
              y2={y(tick)}
              stroke="var(--tk-color-border)"
              strokeWidth="1"
              opacity="0.5"
            />
            <text
              x={PAD_LEFT - 8}
              y={y(tick) + 3}
              textAnchor="end"
              className="fill-text-muted text-[10px]"
            >
              {tick}
            </text>
          </g>
        ))}

        {series.map((serie) => (
          <polyline
            key={serie.name}
            fill="none"
            stroke={serie.color}
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            points={serie.points.map((value, index) => `${x(index)},${y(value)}`).join(' ')}
          />
        ))}

        {series.map((serie) =>
          serie.points.map((value, index) => (
            <circle
              key={`${serie.name}-${index}`}
              cx={x(index)}
              cy={y(value)}
              r="3"
              fill="var(--tk-color-surface)"
              stroke={serie.color}
              strokeWidth="2"
            />
          )),
        )}

        {labels.map((label, index) => (
          <text
            key={label}
            x={x(index)}
            y={HEIGHT - 8}
            textAnchor="middle"
            className="fill-text-muted text-[10px]"
          >
            {label}
          </text>
        ))}
      </svg>
    </div>
  );
}
