import { useEffect, useState } from "react";
import { Link } from "react-router";
import { api, describeError } from "../api/client";
import type { AnalysisListItem } from "../api/types";
import { ErrorMessage, Loading } from "../components/Messages";
import { ScoreBadge } from "../components/ScoreRing";
import { formatDate } from "../format";

export function HistoryPage() {
  const [items, setItems] = useState<AnalysisListItem[] | null>(null);
  const [names, setNames] = useState<Record<number, string>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // The API already returns newest first.
    api.listAnalyses().then(setItems).catch((err) => setError(describeError(err)));
    api
      .listResumes()
      .then((list) => setNames(Object.fromEntries(list.map((r) => [r.id, r.fileName]))))
      .catch(() => undefined);
  }, []);

  return (
    <>
      <h1>History</h1>
      <ErrorMessage>{error}</ErrorMessage>
      {items === null && !error && <Loading />}
      {items?.length === 0 && (
        <section className="card">
          <p className="muted">No analyses yet.</p>
          <Link className="button primary" to="/analyze">
            Analyze a job
          </Link>
        </section>
      )}
      {items && items.length > 0 && (
        <ul className="list card">
          {items.map((a) => (
            <li key={a.id}>
              <Link to={`/results/${a.id}`} className="list-item link-item">
                <ScoreBadge score={a.matchScore} />
                <div className="list-main">
                  <span className="list-title">{a.summary}</span>
                  <span className="muted small">
                    {names[a.resumeId] ? `${names[a.resumeId]} · ` : ""}
                    {formatDate(a.createdAt)}
                  </span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <p className="muted small">Analyses are deleted automatically after 90 days.</p>
    </>
  );
}
