import { describe, expect, it, vi } from "vitest";
import { ApiError, SESSION_EXPIRED, api, describeError, setUnauthorizedHandler, tokenStore } from "./client";
import { apiError, jsonResponse, mockFetch } from "../test/utils";

describe("api client", () => {
  it("sends the saved token and parses JSON", async () => {
    tokenStore.set("abc");
    const fetch = mockFetch(() => jsonResponse([{ id: 1, fileName: "a.pdf", characterCount: 10, createdAt: "2026-11-06T10:00:00Z" }]));
    const list = await api.listResumes();
    expect(list[0].fileName).toBe("a.pdf");
    expect((fetch.mock.calls[0][1] as RequestInit).headers).toMatchObject({ Authorization: "Bearer abc" });
  });

  it("turns API errors into ApiError with message, field errors and Retry-After", async () => {
    mockFetch(() => apiError(429, "Too many failed login attempts. Try again in 15 minutes.", {}, { "Retry-After": "900" }));
    const err = await api.login("a@example.com", "x").catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(429);
    expect(err.message).toBe("Too many failed login attempts. Try again in 15 minutes.");
    expect(err.retryAfterSeconds).toBe(900);
  });

  it("describes validation errors field by field", () => {
    const err = new ApiError(400, "Validation failed", {
      email: "must be a well-formed email address",
      password: "size must be between 8 and 72",
    });
    expect(describeError(err)).toBe("Email must be a well-formed email address. Password size must be between 8 and 72.");
  });

  it("logs out on 401 when a token was sent", async () => {
    tokenStore.set("expired");
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    mockFetch(() => apiError(401, "Invalid or expired token"));

    const err = await api.me().catch((e) => e);
    expect(err.message).toBe(SESSION_EXPIRED);
    expect(onUnauthorized).toHaveBeenCalledOnce();
    expect(tokenStore.get()).toBeNull();
    setUnauthorizedHandler(null);
  });

  it("does not treat a wrong password at login as an expired session", async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    mockFetch(() => apiError(401, "Invalid email or password"));

    const err = await api.login("a@example.com", "wrong").catch((e) => e);
    expect(err.message).toBe("Invalid email or password");
    expect(onUnauthorized).not.toHaveBeenCalled();
    setUnauthorizedHandler(null);
  });

  it("explains network failures", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))));
    const err = await api.privacy().catch((e) => e);
    expect(err.status).toBe(0);
    expect(err.message).toMatch(/Can't reach the server/);
  });

  it("uploads resumes as multipart without forcing a Content-Type", async () => {
    tokenStore.set("abc");
    const fetch = mockFetch(() => jsonResponse({ id: 1, fileName: "cv.pdf", characterCount: 900, createdAt: "", extractedText: "" }, 201));
    await api.uploadResume(new File(["%PDF-"], "cv.pdf", { type: "application/pdf" }));
    const init = fetch.mock.calls[0][1] as RequestInit;
    expect(init.body).toBeInstanceOf(FormData);
    expect(init.headers).not.toHaveProperty("Content-Type");
  });
});
