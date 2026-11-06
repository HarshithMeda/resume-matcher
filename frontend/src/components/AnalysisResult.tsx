import type { Analysis, MatchedSkill, MissingSkill } from "../api/types";
import { formatDate } from "../format";
import { ScoreRing } from "./ScoreRing";

/** Everything about one analysis, laid out to read at a glance. */
export function AnalysisResult({ analysis, resumeName }: { analysis: Analysis; resumeName?: string }) {
  const required = analysis.matchedSkills.filter((s) => s.importance === "REQUIRED");
  const preferred = analysis.matchedSkills.filter((s) => s.importance === "PREFERRED");

  return (
    <div className="result">
      <section className="card result-hero">
        <ScoreRing score={analysis.matchScore} />
        <div className="result-hero-text">
          <p className="summary">{analysis.summary}</p>
          <p className="muted small">
            {resumeName ? `${resumeName} · ` : ""}
            {formatDate(analysis.createdAt)}
          </p>
        </div>
      </section>

      <section className="card">
        <h2>Score breakdown</h2>
        <ul className="breakdown">
          {analysis.scoreBreakdown.components.map((c) => (
            <li key={c.name} className={c.weight === 0 ? "inactive" : undefined}>
              <div className="breakdown-row">
                <span className="breakdown-name">{c.name}</span>
                <span className="breakdown-points">
                  {c.weight === 0 ? "—" : `${c.points} / ${c.weight}`}
                </span>
              </div>
              <div className="bar" aria-hidden="true">
                <div className="bar-fill" style={{ width: `${c.weight === 0 ? 0 : (c.points / c.weight) * 100}%` }} />
              </div>
              <p className="muted small">{c.explanation}</p>
            </li>
          ))}
        </ul>
        <p className="muted small formula">{analysis.scoreBreakdown.formula}</p>
      </section>

      <div className="grid-2">
        <section className="card">
          <h2>Missing required skills</h2>
          <MissingChips skills={analysis.missingRequiredSkills} tone="bad" empty="None. You cover every required skill." />
        </section>
        <section className="card">
          <h2>Missing nice-to-haves</h2>
          <MissingChips skills={analysis.missingPreferredSkills} tone="warn" empty="None." />
        </section>
      </div>

      <section className="card">
        <h2>Skills you have</h2>
        {analysis.matchedSkills.length === 0 ? (
          <p className="muted">None of the job's skills were found in this resume.</p>
        ) : (
          <>
            {required.length > 0 && <MatchedChips title="Required" skills={required} />}
            {preferred.length > 0 && <MatchedChips title="Nice to have" skills={preferred} />}
          </>
        )}
      </section>

      <section className="card">
        <h2>Keywords from the job</h2>
        <div className="keyword-groups">
          <div>
            <h3>In your resume</h3>
            <KeywordList words={analysis.matchedKeywords} tone="good" empty="None yet." />
          </div>
          <div>
            <h3>Not in your resume</h3>
            <KeywordList words={analysis.missingKeywords} tone="muted" empty="None. Nice!" />
          </div>
        </div>
      </section>

      <section className="card tips">
        <h2>Tips</h2>
        <ol>
          {analysis.tips.map((tip) => (
            <li key={tip}>{tip}</li>
          ))}
        </ol>
      </section>
    </div>
  );
}

function MatchedChips({ title, skills }: { title: string; skills: MatchedSkill[] }) {
  return (
    <div className="chip-group">
      <h3>{title}</h3>
      <ul className="chips">
        {skills.map((s) => (
          <li key={s.name} className="chip good">
            <span className="chip-name">
              <span aria-hidden="true">✓ </span>
              {s.name}
            </span>
            {s.impliedBy && <span className="chip-note">implied by {s.impliedBy}</span>}
            {s.eitherOr && <span className="chip-note">any one is enough · matched: {matchedOptions(s)}</span>}
            {s.subSkills && s.subSkills.length > 0 && (
              <ul className="sub-skills" aria-label={s.eitherOr ? `${s.name}: options` : `${s.name} includes`}>
                {s.subSkills.map((sub) => (
                  <li key={sub.name} className={sub.found ? "found" : "not-found"}>
                    <span aria-hidden="true">{sub.found ? "✓" : "✗"} </span>
                    {sub.name}
                    <span className="visually-hidden">{sub.found ? " (found)" : " (not found)"}</span>
                    {sub.impliedBy && <span className="chip-note"> implied by {sub.impliedBy}</span>}
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

/** "Tableau", or "Power BI and Tableau" if the resume has several of the options. */
function matchedOptions(s: MatchedSkill): string {
  return (s.subSkills ?? [])
    .filter((o) => o.found)
    .map((o) => o.name)
    .join(" and ");
}

function MissingChips({ skills, tone, empty }: { skills: MissingSkill[]; tone: "bad" | "warn"; empty: string }) {
  if (skills.length === 0) {
    return <p className="muted">{empty}</p>;
  }
  return (
    <ul className="chips">
      {skills.map((s) => (
        <li key={s.name} className={`chip ${tone}`}>
          <span className="chip-name">{s.name}</span>
          {s.eitherOr ? (
            <span className="chip-note">any one is enough</span>
          ) : (
            s.subSkills &&
            s.subSkills.length > 0 && <span className="chip-note">any of: {s.subSkills.map((x) => x.name).join(", ")}</span>
          )}
        </li>
      ))}
    </ul>
  );
}

function KeywordList({ words, tone, empty }: { words: string[]; tone: "good" | "muted"; empty: string }) {
  if (words.length === 0) {
    return <p className="muted small">{empty}</p>;
  }
  return (
    <ul className="chips">
      {words.map((w) => (
        <li key={w} className={`chip keyword ${tone}`}>
          {w}
        </li>
      ))}
    </ul>
  );
}
