import { useEffect, useState } from "react";
import { Link } from "react-router";
import { api, describeError } from "../api/client";
import type { ResumeSummary } from "../api/types";
import { Dropzone } from "../components/Dropzone";
import { ErrorMessage, Loading, Notice } from "../components/Messages";
import { formatDate, formatNumber } from "../format";

export const MAX_RESUMES = 5;
export const MAX_FILE_BYTES = 2 * 1024 * 1024;

export function ResumesPage() {
  const [resumes, setResumes] = useState<ResumeSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null);

  useEffect(() => {
    api.listResumes().then(setResumes).catch((err) => setError(describeError(err)));
  }, []);

  async function upload(file: File) {
    setError(null);
    setSuccess(null);
    // Same limit as the server, checked here first so a big file isn't uploaded for nothing.
    if (file.size > MAX_FILE_BYTES) {
      setError("File is too large (maximum 2 MB)");
      return;
    }
    setUploading(true);
    try {
      const saved = await api.uploadResume(file);
      setSuccess(`Saved "${saved.fileName}" (${formatNumber(saved.characterCount)} characters of text).`);
      setResumes(await api.listResumes());
    } catch (err) {
      // The server explains: not a PDF, too large, scanned, too many pages, password-protected, limit of 5.
      setError(describeError(err));
    } finally {
      setUploading(false);
    }
  }

  async function remove(id: number) {
    setError(null);
    setSuccess(null);
    try {
      await api.deleteResume(id);
      setResumes((list) => list?.filter((r) => r.id !== id) ?? null);
      setSuccess("Resume deleted, along with its analyses.");
    } catch (err) {
      setError(describeError(err));
    } finally {
      setConfirmDelete(null);
    }
  }

  const atLimit = (resumes?.length ?? 0) >= MAX_RESUMES;

  return (
    <>
      <h1>Your resumes</h1>

      <section className="card">
        <Dropzone onFile={upload} disabled={uploading || atLimit} />
        {uploading && <Loading label="Uploading and reading your PDF…" />}
        {atLimit && <p className="muted small">You've saved {MAX_RESUMES} resumes, the maximum. Delete one to upload another.</p>}
        <ErrorMessage>{error}</ErrorMessage>
        <Notice tone="success">{success}</Notice>
      </section>

      <section className="card">
        <div className="section-head">
          <h2>Saved resumes</h2>
          {resumes && (
            <span className="muted small">
              {resumes.length} of {MAX_RESUMES}
            </span>
          )}
        </div>
        {resumes === null && !error && <Loading />}
        {resumes?.length === 0 && <p className="muted">No resumes yet. Upload a PDF above to get started.</p>}
        {resumes && resumes.length > 0 && (
          <ul className="list">
            {resumes.map((r) => (
              <li key={r.id} className="list-item">
                <div className="list-main">
                  <span className="list-title">{r.fileName}</span>
                  <span className="muted small">
                    {formatDate(r.createdAt)} · {formatNumber(r.characterCount)} characters
                  </span>
                </div>
                <div className="list-actions">
                  <Link className="button small" to={`/analyze?resume=${r.id}`}>
                    Analyze
                  </Link>
                  {confirmDelete === r.id ? (
                    <>
                      <button type="button" className="button danger small" onClick={() => remove(r.id)}>
                        Confirm delete
                      </button>
                      <button type="button" className="button ghost small" onClick={() => setConfirmDelete(null)}>
                        Cancel
                      </button>
                    </>
                  ) : (
                    <button type="button" className="button ghost small" onClick={() => setConfirmDelete(r.id)} aria-label={`Delete ${r.fileName}`}>
                      Delete
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
