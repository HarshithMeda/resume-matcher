import { useEffect, useState, type FormEvent } from "react";
import { api, describeError } from "../api/client";
import type { PrivacyNotice, User } from "../api/types";
import { useAuth } from "../auth/useAuth";
import { ErrorMessage, Loading, Notice } from "../components/Messages";
import { formatDate } from "../format";

export function AccountPage() {
  const { logOut } = useAuth();
  const [user, setUser] = useState<User | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exported, setExported] = useState(false);

  const [password, setPassword] = useState("");
  const [understood, setUnderstood] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    api.me().then(setUser).catch(() => undefined);
  }, []);

  async function download() {
    setExportError(null);
    setExported(false);
    setExporting(true);
    try {
      const { blob, fileName } = await api.exportData();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setExported(true);
    } catch (err) {
      setExportError(describeError(err));
    } finally {
      setExporting(false);
    }
  }

  async function deleteAccount(e: FormEvent) {
    e.preventDefault();
    setDeleteError(null);
    setDeleting(true);
    try {
      await api.deleteAccount(password);
      logOut("Your account and all its data have been deleted.");
    } catch (err) {
      // 403 "Incorrect password", or 429 after too many wrong passwords.
      setDeleteError(describeError(err));
      setDeleting(false);
    }
  }

  return (
    <>
      <h1>Account</h1>

      <section className="card">
        <h2>Your account</h2>
        {user ? (
          <p>
            {user.email}
            <br />
            <span className="muted small">Member since {formatDate(user.createdAt)}</span>
          </p>
        ) : (
          <Loading />
        )}
      </section>

      <section className="card">
        <h2>Download your data</h2>
        <p className="muted">Everything we store about you (account, resume text and analyses) as a JSON file.</p>
        <button type="button" className="button" onClick={download} disabled={exporting}>
          {exporting ? "Preparing…" : "Download my data"}
        </button>
        <ErrorMessage>{exportError}</ErrorMessage>
        {exported && <Notice tone="success">Your download has started.</Notice>}
      </section>

      <PrivacySection />

      <section className="card danger-zone">
        <h2>Delete account</h2>
        <p>This permanently deletes your account, all your resumes and all your analyses. It can't be undone.</p>
        <form className="form" onSubmit={deleteAccount}>
          <label className="field">
            <span>Confirm with your password</span>
            <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
          <label className="checkbox">
            <input type="checkbox" checked={understood} onChange={(e) => setUnderstood(e.target.checked)} />
            <span>I understand this deletes everything.</span>
          </label>
          <ErrorMessage>{deleteError}</ErrorMessage>
          <button type="submit" className="button danger" disabled={!password || !understood || deleting}>
            {deleting ? "Deleting…" : "Delete my account"}
          </button>
        </form>
      </section>
    </>
  );
}

/** The privacy note from /api/privacy. Also shown on its own page before login. */
export function PrivacySection() {
  const [notice, setNotice] = useState<PrivacyNotice | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.privacy().then(setNotice).catch((err) => setError(describeError(err)));
  }, []);

  return (
    <section className="card privacy">
      <h2>Privacy</h2>
      <ErrorMessage>{error}</ErrorMessage>
      {!notice && !error && <Loading />}
      {notice && (
        <>
          <p>{notice.summary}</p>
          <div className="grid-2">
            <div>
              <h3>What we store</h3>
              <ul>
                {notice.whatWeStore.map((s) => (
                  <li key={s}>{s}</li>
                ))}
              </ul>
            </div>
            <div>
              <h3>What we never store</h3>
              <ul>
                {notice.whatWeNeverStore.map((s) => (
                  <li key={s}>{s}</li>
                ))}
              </ul>
            </div>
          </div>
          <h3>How long we keep it</h3>
          <p>{notice.retention}</p>
          <h3>Your controls</h3>
          <ul>
            {notice.yourControls.map((s) => (
              <li key={s}>{s}</li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
