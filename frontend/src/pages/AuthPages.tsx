import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router";
import { api, describeError } from "../api/client";
import { useAuth } from "../auth/useAuth";
import { ErrorMessage, Notice } from "../components/Messages";

type Mode = "login" | "register";

export function LoginPage() {
  return <AuthForm mode="login" />;
}

export function RegisterPage() {
  return <AuthForm mode="register" />;
}

function AuthForm({ mode }: { mode: Mode }) {
  const { token, notice, logIn, clearNotice } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (token) {
    return <Navigate to="/resumes" replace />;
  }

  const isRegister = mode === "register";
  const from = (location.state as { from?: string } | null)?.from;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    clearNotice();
    setBusy(true);
    try {
      if (isRegister) {
        await api.register(email, password);
      }
      const { accessToken } = await api.login(email, password);
      logIn(accessToken);
      navigate(from && from !== "/login" ? from : "/resumes", { replace: true });
    } catch (err) {
      // 401 bad credentials, 409 email taken, 400 validation, 429 lockout: the API's message says it all.
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="card auth-card">
        <h1 className="auth-title">
          <img src="/favicon.svg" alt="" width="32" height="32" />
          Resume Matcher
        </h1>
        <p className="muted">
          {isRegister ? "Create an account to compare your resume with job descriptions." : "Log in to compare your resume with job descriptions."}
        </p>

        <Notice>{notice}</Notice>
        <ErrorMessage>{error}</ErrorMessage>

        <form onSubmit={submit} className="form" noValidate>
          <label className="field">
            <span>Email</span>
            <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete={isRegister ? "new-password" : "current-password"}
              required
              minLength={isRegister ? 8 : undefined}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-describedby={isRegister ? "password-hint" : undefined}
            />
            {isRegister && (
              <small id="password-hint" className="muted">
                At least 8 characters.
              </small>
            )}
          </div>
          <button type="submit" className="button primary wide" disabled={busy || !email || !password}>
            {busy ? "Please wait…" : isRegister ? "Create account" : "Log in"}
          </button>
        </form>

        <p className="muted small center">
          {isRegister ? (
            <>
              Already have an account? <Link to="/login">Log in</Link>
            </>
          ) : (
            <>
              New here? <Link to="/register">Create an account</Link>
            </>
          )}
        </p>
        <p className="muted small center">
          <Link to="/privacy">How we handle your data</Link>
        </p>
      </div>
    </div>
  );
}
