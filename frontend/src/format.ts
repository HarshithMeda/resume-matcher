export function formatDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export function formatNumber(n: number): string {
  return n.toLocaleString("en-US");
}

/** Same bands as the backend summary. */
export function scoreBand(score: number): { label: string; tone: "strong" | "good" | "partial" | "weak" } {
  if (score >= 80) return { label: "Strong match", tone: "strong" };
  if (score >= 60) return { label: "Good match", tone: "good" };
  if (score >= 40) return { label: "Partial match", tone: "partial" };
  return { label: "Weak match", tone: "weak" };
}
