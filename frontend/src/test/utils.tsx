import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { vi } from "vitest";
import { App } from "../App";
import { tokenStore } from "../api/client";
import { AuthProvider } from "../auth/AuthContext";

type Handler = (url: string, init: RequestInit) => Response | Promise<Response>;

/** Replace fetch with a handler; returns the mock so tests can inspect calls. */
export function mockFetch(handler: Handler) {
  const fn = vi.fn((input: RequestInfo | URL, init: RequestInit = {}) => Promise.resolve(handler(String(input), init)));
  vi.stubGlobal("fetch", fn);
  return fn;
}

export function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

export function apiError(status: number, message: string, extra: Record<string, unknown> = {}, headers: Record<string, string> = {}) {
  return jsonResponse({ status, error: "Error", message, ...extra }, status, headers);
}

/** Renders the whole app at a path, optionally logged in. */
export function renderApp(path: string, { token }: { token?: string } = {}) {
  if (token) tokenStore.set(token);
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}
