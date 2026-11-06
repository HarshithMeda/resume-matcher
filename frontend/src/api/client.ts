import type {
  Analysis,
  AnalysisListItem,
  ApiErrorBody,
  PrivacyNotice,
  ResumeDetail,
  ResumeSummary,
  TokenResponse,
  User,
} from "./types";

// ---- token storage ----
// localStorage for now (simple; survives reloads). Access is wrapped because it can throw
// in private browsing or when storage is blocked.

const TOKEN_KEY = "resumeMatcher.token";

export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string) {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      // Storage unavailable: the user stays logged in until the page reloads.
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      // ignore
    }
  },
};

// Called when a logged-in request gets 401 (token expired or account deleted).
let unauthorizedHandler: (() => void) | null = null;
export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

// ---- errors ----

export const SESSION_EXPIRED = "Your session has expired. Please log in again.";
const NETWORK_ERROR = "Can't reach the server. Check your connection and try again.";

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;
  readonly retryAfterSeconds: number | null;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}, retryAfterSeconds: number | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

const FIELD_LABELS: Record<string, string> = {
  email: "Email",
  password: "Password",
  jobDescription: "Job description",
  resumeId: "Resume",
  file: "File",
};

/** One readable sentence for any error, e.g. "Email must be a well-formed email address." */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const fields = Object.entries(error.fieldErrors);
    if (fields.length > 0) {
      return fields.map(([field, problem]) => `${FIELD_LABELS[field] ?? field} ${problem}.`).join(" ");
    }
    return error.message;
  }
  return "Something went wrong. Please try again.";
}

// ---- requests ----

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** Send the saved token (default true). Login and register don't. */
  auth?: boolean;
}

async function send(path: string, { method = "GET", body, auth = true }: RequestOptions = {}): Promise<Response> {
  const headers: Record<string, string> = { Accept: "application/json" };
  const token = auth ? tokenStore.get() : null;
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  let payload: BodyInit | undefined;
  if (body instanceof FormData) {
    payload = body; // the browser sets the multipart Content-Type with its boundary
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }

  let res: Response;
  try {
    res = await fetch(path, { method, headers, body: payload });
  } catch {
    throw new ApiError(0, NETWORK_ERROR);
  }

  if (res.status === 401 && token) {
    // A token we sent was rejected: expired, or the account no longer exists.
    tokenStore.clear();
    unauthorizedHandler?.();
    throw new ApiError(401, SESSION_EXPIRED);
  }
  if (!res.ok) {
    throw await toApiError(res);
  }
  return res;
}

async function toApiError(res: Response): Promise<ApiError> {
  let body: Partial<ApiErrorBody> = {};
  try {
    body = (await res.json()) as ApiErrorBody;
  } catch {
    // Not JSON (e.g. a proxy error page): fall back to a generic message below.
  }
  const retryAfter = Number(res.headers.get("Retry-After"));
  return new ApiError(
    res.status,
    body.message || defaultMessage(res.status),
    body.fieldErrors ?? {},
    Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null,
  );
}

function defaultMessage(status: number): string {
  if (status === 429) return "Too many requests. Please wait a bit and try again.";
  if (status >= 500) return "The server had a problem. Please try again in a moment.";
  return "The request couldn't be completed.";
}

async function json<T>(path: string, options?: RequestOptions): Promise<T> {
  const res = await send(path, options);
  return (await res.json()) as T;
}

async function noContent(path: string, options?: RequestOptions): Promise<void> {
  await send(path, options);
}

export const api = {
  register: (email: string, password: string) =>
    json<User>("/api/auth/register", { method: "POST", body: { email, password }, auth: false }),
  login: (email: string, password: string) =>
    json<TokenResponse>("/api/auth/login", { method: "POST", body: { email, password }, auth: false }),

  me: () => json<User>("/api/me"),
  deleteAccount: (password: string) => noContent("/api/me", { method: "DELETE", body: { password } }),
  exportData: async (): Promise<{ blob: Blob; fileName: string }> => {
    const res = await send("/api/me/export");
    const disposition = res.headers.get("Content-Disposition") ?? "";
    const match = /filename="?([^";]+)"?/.exec(disposition);
    return { blob: await res.blob(), fileName: match?.[1] ?? "resume-matcher-export.json" };
  },

  listResumes: () => json<ResumeSummary[]>("/api/resumes"),
  uploadResume: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return json<ResumeDetail>("/api/resumes", { method: "POST", body: form });
  },
  deleteResume: (id: number) => noContent(`/api/resumes/${id}`, { method: "DELETE" }),

  analyze: (resumeId: number, jobDescription: string) =>
    json<Analysis>("/api/analyses", { method: "POST", body: { resumeId, jobDescription } }),
  listAnalyses: () => json<AnalysisListItem[]>("/api/analyses"),
  getAnalysis: (id: number) => json<Analysis>(`/api/analyses/${id}`),

  privacy: () => json<PrivacyNotice>("/api/privacy", { auth: false }),
};
