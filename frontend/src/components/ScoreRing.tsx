import { scoreBand } from "../format";

/** The big match score: a ring that fills to the score, with the number in the middle. */
export function ScoreRing({ score, size = 148 }: { score: number; size?: number }) {
  const { label, tone } = scoreBand(score);
  const stroke = 12;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const filled = (Math.max(0, Math.min(100, score)) / 100) * circumference;

  return (
    <div className={`score-ring tone-${tone}`} style={{ width: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={`Match score ${score} out of 100: ${label}`}>
        <circle className="ring-track" cx={size / 2} cy={size / 2} r={radius} strokeWidth={stroke} fill="none" />
        <circle
          className="ring-fill"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={stroke}
          fill="none"
          strokeLinecap="round"
          strokeDasharray={`${filled} ${circumference}`}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
        <text x="50%" y="48%" textAnchor="middle" dominantBaseline="middle" className="ring-number">
          {score}
        </text>
        <text x="50%" y="68%" textAnchor="middle" dominantBaseline="middle" className="ring-sub">
          out of 100
        </text>
      </svg>
      <div className="ring-label">{label}</div>
    </div>
  );
}

/** Small score pill for lists. */
export function ScoreBadge({ score }: { score: number }) {
  const { tone, label } = scoreBand(score);
  return (
    <span className={`score-badge tone-${tone}`} title={label}>
      {score}
    </span>
  );
}
