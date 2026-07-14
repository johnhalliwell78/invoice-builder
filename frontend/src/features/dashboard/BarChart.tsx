import { useId, useState } from 'react';

export interface BarDatum {
  label: string;
  value: number;
  display: string;
}

/**
 * Single-series vertical bar chart, hand-rolled SVG (no chart dependency).
 * Follows the dataviz mark specs: thin bars with 4px rounded tops anchored to
 * the baseline, a 2px surface gap between bars, recessive axis, and a per-bar
 * hover tooltip. One hue (the app accent) — a single series needs no legend.
 */
export function BarChart({ data, height = 160 }: { data: BarDatum[]; height?: number }) {
  const clipId = useId();
  const [hover, setHover] = useState<number | null>(null);
  const max = Math.max(1, ...data.map((d) => d.value));
  const n = Math.max(1, data.length);
  const gap = 2;
  const slot = 100 / n;

  return (
    <div className="relative">
      <svg
        viewBox={`0 0 100 ${height}`}
        preserveAspectRatio="none"
        role="img"
        className="w-full"
        style={{ height }}
      >
        <line
          x1="0"
          y1={height - 20}
          x2="100"
          y2={height - 20}
          stroke="currentColor"
          strokeWidth="0.3"
          className="text-border"
          vectorEffect="non-scaling-stroke"
        />
        <clipPath id={clipId}>
          <rect x="0" y="0" width="100" height={height} />
        </clipPath>
        {data.map((d, i) => {
          const barMax = height - 24;
          const h = d.value === 0 ? 0 : Math.max(2, (d.value / max) * barMax);
          const x = i * slot + gap / 2;
          const w = slot - gap;
          const y = height - 20 - h;
          return (
            <rect
              key={d.label}
              x={x}
              y={y}
              width={w}
              height={h}
              rx="1.5"
              className={hover === i ? 'fill-primary' : 'fill-primary/70'}
              clipPath={`url(#${clipId})`}
              onMouseEnter={() => setHover(i)}
              onMouseLeave={() => setHover(null)}
            />
          );
        })}
      </svg>
      <div className="mt-1 flex justify-between text-[10px] text-muted-foreground">
        <span>{data[0]?.label}</span>
        <span>{data[data.length - 1]?.label}</span>
      </div>
      {hover !== null && data[hover] && (
        <div
          className="pointer-events-none absolute -top-1 z-10 -translate-y-full rounded-md border bg-popover px-2 py-1 text-xs shadow-md"
          style={{ left: `${(hover + 0.5) * slot}%`, transform: 'translate(-50%, -100%)' }}
        >
          <div className="font-medium">{data[hover].label}</div>
          <div className="tabular-nums text-muted-foreground">{data[hover].display}</div>
        </div>
      )}
    </div>
  );
}
