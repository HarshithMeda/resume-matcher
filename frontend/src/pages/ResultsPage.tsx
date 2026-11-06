import { useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router";
import { api, describeError } from "../api/client";
import type { Analysis } from "../api/types";
import { AnalysisResult } from "../components/AnalysisResult";
import { ErrorMessage, Loading } from "../components/Messages";

export function ResultsPage() {
  const { id } = useParams();
  const location = useLocation();
  // Coming straight from the Analyze page, the result is passed along; otherwise load it.
  const passed = (location.state as { analysis?: Analysis } | null)?.analysis;
  const [analysis, setAnalysis] = useState<Analysis | null>(passed && String(passed.id) === id ? passed : null);
  const [resumeName, setResumeName] = useState<string | undefined>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!analysis || String(analysis.id) !== id) {
      api.getAnalysis(Number(id)).then(setAnalysis).catch((err) => setError(describeError(err)));
    }
  }, [id, analysis]);

  useEffect(() => {
    if (analysis) {
      api
        .listResumes()
        .then((list) => setResumeName(list.find((r) => r.id === analysis.resumeId)?.fileName))
        .catch(() => undefined); // the name is a nice-to-have
    }
  }, [analysis]);

  return (
    <>
      <div className="section-head">
        <h1>Match result</h1>
        <div className="row">
          <Link className="button small" to={analysis ? `/analyze?resume=${analysis.resumeId}` : "/analyze"}>
            New analysis
          </Link>
          <Link className="button ghost small" to="/history">
            History
          </Link>
        </div>
      </div>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {!error && !analysis && <Loading />}
      {analysis && <AnalysisResult analysis={analysis} resumeName={resumeName} />}
    </>
  );
}
