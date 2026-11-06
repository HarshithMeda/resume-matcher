import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { api, describeError } from "../api/client";
import type { ResumeSummary } from "../api/types";
import { ErrorMessage, Loading } from "../components/Messages";
import { formatDate, formatNumber } from "../format";

export const MIN_JD = 50;
export const MAX_JD = 15_000;

export function AnalyzePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [resumes, setResumes] = useState<ResumeSummary[] | null>(null);
  const [resumeId, setResumeId] = useState<string>("");
  const [jobDescription, setJobDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api
      .listResumes()
      .then((list) => {
        setResumes(list);
        const fromLink = params.get("resume");
        const preselect = list.find((r) => String(r.id) === fromLink) ?? list[0];
        if (preselect) setResumeId(String(preselect.id));
      })
      .catch((err) => setError(describeError(err)));
  }, [params]);

  const length = jobDescription.length;
  const tooShort = length < MIN_JD;
  const tooLong = length > MAX_JD;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const analysis = await api.analyze(Number(resumeId), jobDescription);
      navigate(`/results/${analysis.id}`, { state: { analysis } });
    } catch (err) {
      // e.g. the daily limit: "You've reached the limit of 30 analyses per day. It resets at 00:00 UTC, in …"
      setError(describeError(err));
      setBusy(false);
    }
  }

  if (resumes === null) {
    return (
      <>
        <h1>Analyze a job</h1>
        {error ? <ErrorMessage>{error}</ErrorMessage> : <Loading />}
      </>
    );
  }

  if (resumes.length === 0) {
    return (
      <>
        <h1>Analyze a job</h1>
        <section className="card">
          <p>First, upload a resume.</p>
          <Link className="button primary" to="/resumes">
            Upload a resume
          </Link>
        </section>
      </>
    );
  }

  return (
    <>
      <h1>Analyze a job</h1>
      <form className="card form" onSubmit={submit}>
        <label className="field">
          <span>Resume</span>
          <select value={resumeId} onChange={(e) => setResumeId(e.target.value)}>
            {resumes.map((r) => (
              <option key={r.id} value={r.id}>
                {r.fileName} ({formatDate(r.createdAt)})
              </option>
            ))}
          </select>
        </label>

        <div className="field">
          <label htmlFor="job-description">Job description</label>
          <textarea
            id="job-description"
            rows={14}
            value={jobDescription}
            onChange={(e) => setJobDescription(e.target.value)}
            placeholder="Paste the full job posting here, including requirements and nice-to-haves."
            aria-describedby="jd-counter jd-note"
          />
          <span id="jd-counter" className={`counter${tooLong ? " over" : ""}`} aria-live="polite">
            {formatNumber(length)} / {formatNumber(MAX_JD)} characters
            {length > 0 && tooShort && ` · at least ${MIN_JD} needed`}
            {tooLong && ` · ${formatNumber(length - MAX_JD)} too many`}
          </span>
          <small id="jd-note" className="muted">
            The job description is used for this analysis only and is never saved.
          </small>
        </div>

        <ErrorMessage>{error}</ErrorMessage>

        <button type="submit" className="button primary wide" disabled={busy || tooShort || tooLong || !resumeId}>
          {busy ? "Analyzing…" : "Analyze match"}
        </button>
      </form>
    </>
  );
}
