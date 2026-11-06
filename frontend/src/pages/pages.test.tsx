import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { tokenStore } from "../api/client";
import { apiError, jsonResponse, mockFetch, renderApp } from "../test/utils";

const RESUME = { id: 3, fileName: "cv.pdf", characterCount: 785, createdAt: "2026-11-06T10:00:00Z" };

describe("login and register", () => {
  it("logs in and goes to the resumes page", async () => {
    mockFetch((url) => {
      if (url === "/api/auth/login") return jsonResponse({ accessToken: "tok", tokenType: "Bearer", expiresIn: 3600 });
      if (url === "/api/resumes") return jsonResponse([]);
      throw new Error("unexpected " + url);
    });
    renderApp("/login");
    await userEvent.type(screen.getByLabelText("Email"), "pat@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "correct-horse");
    await userEvent.click(screen.getByRole("button", { name: "Log in" }));

    expect(await screen.findByRole("heading", { name: "Your resumes" })).toBeInTheDocument();
    expect(tokenStore.get()).toBe("tok");
  });

  it("shows the lockout message on 429", async () => {
    mockFetch(() => apiError(429, "Too many failed login attempts. Try again in 15 minutes.", {}, { "Retry-After": "900" }));
    renderApp("/login");
    await userEvent.type(screen.getByLabelText("Email"), "pat@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "guess");
    await userEvent.click(screen.getByRole("button", { name: "Log in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Too many failed login attempts. Try again in 15 minutes.");
  });

  it("shows register errors from the API", async () => {
    mockFetch(() => apiError(409, "An account with this email already exists"));
    renderApp("/register");
    await userEvent.type(screen.getByLabelText("Email"), "pat@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "long-enough-password");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("An account with this email already exists");
  });
});

describe("expired session", () => {
  it("goes back to the login screen with a message when the API returns 401", async () => {
    mockFetch(() => apiError(401, "Invalid or expired token"));
    renderApp("/resumes", { token: "old-token" });
    expect(await screen.findByRole("button", { name: "Log in" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Your session has expired. Please log in again.");
    expect(tokenStore.get()).toBeNull();
  });

  it("sends visitors without a token to login", async () => {
    renderApp("/history");
    expect(await screen.findByRole("button", { name: "Log in" })).toBeInTheDocument();
  });
});

describe("resumes page", () => {
  it("shows the API's reason when an upload is rejected", async () => {
    mockFetch((url, init) => {
      if (url === "/api/resumes" && init.method === "POST") {
        return apiError(422, "This PDF has almost no selectable text. It looks like a scanned image, and scanned resumes aren't supported yet.");
      }
      return jsonResponse([]);
    });
    renderApp("/resumes", { token: "tok" });
    await screen.findByText(/No resumes yet/);

    const input = screen.getByLabelText("Resume PDF");
    await userEvent.upload(input, new File(["%PDF-1.7"], "scan.pdf", { type: "application/pdf" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("scanned resumes aren't supported yet");
  });

  it("rejects files over 2 MB before uploading", async () => {
    const fetch = mockFetch(() => jsonResponse([]));
    renderApp("/resumes", { token: "tok" });
    await screen.findByText(/No resumes yet/);

    const big = new File([new Uint8Array(2 * 1024 * 1024 + 1)], "big.pdf", { type: "application/pdf" });
    fireEvent.drop(screen.getByTestId("dropzone"), { dataTransfer: { files: [big] } });
    expect(await screen.findByRole("alert")).toHaveTextContent("File is too large (maximum 2 MB)");
    expect(fetch.mock.calls.filter(([, init]) => (init as RequestInit)?.method === "POST")).toHaveLength(0);
  });

  it("deletes a resume after confirmation", async () => {
    let resumes = [RESUME];
    mockFetch((_url, init) => {
      if (init.method === "DELETE") {
        resumes = [];
        return new Response(null, { status: 204 });
      }
      return jsonResponse(resumes);
    });
    renderApp("/resumes", { token: "tok" });
    await userEvent.click(await screen.findByRole("button", { name: "Delete cv.pdf" }));
    await userEvent.click(screen.getByRole("button", { name: "Confirm delete" }));
    await waitFor(() => expect(screen.queryByText("cv.pdf")).not.toBeInTheDocument());
    expect(screen.getByRole("status")).toHaveTextContent("Resume deleted");
  });

  it("disables uploads at the limit of 5", async () => {
    mockFetch(() => jsonResponse([1, 2, 3, 4, 5].map((id) => ({ ...RESUME, id, fileName: `cv${id}.pdf` }))));
    renderApp("/resumes", { token: "tok" });
    expect(await screen.findByText(/You've saved 5 resumes, the maximum/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Choose a PDF" })).toBeDisabled();
  });
});

describe("analyze page", () => {
  it("counts characters and only allows 50 to 15,000", async () => {
    mockFetch(() => jsonResponse([RESUME]));
    renderApp("/analyze", { token: "tok" });
    const textarea = await screen.findByLabelText("Job description");
    const submit = screen.getByRole("button", { name: "Analyze match" });

    fireEvent.change(textarea, { target: { value: "x".repeat(49) } });
    expect(screen.getByText(/49 \/ 15,000 characters · at least 50 needed/)).toBeInTheDocument();
    expect(submit).toBeDisabled();

    fireEvent.change(textarea, { target: { value: "x".repeat(50) } });
    expect(submit).toBeEnabled();

    fireEvent.change(textarea, { target: { value: "x".repeat(15_001) } });
    expect(screen.getByText(/15,001 \/ 15,000 characters · 1 too many/)).toBeInTheDocument();
    expect(submit).toBeDisabled();
  });

  it("shows the daily limit message", async () => {
    mockFetch((_url, init) =>
      init.method === "POST"
        ? apiError(429, "You've reached the limit of 30 analyses per day. It resets at 00:00 UTC, in 2 hours.")
        : jsonResponse([RESUME]),
    );
    renderApp("/analyze", { token: "tok" });
    fireEvent.change(await screen.findByLabelText("Job description"), { target: { value: "Java developer ".repeat(10) } });
    await userEvent.click(screen.getByRole("button", { name: "Analyze match" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("30 analyses per day");
  });
});

describe("account page", () => {
  it("deletes the account with the password and returns to login", async () => {
    const fetch = mockFetch((url, init) => {
      if (url === "/api/me" && init.method === "DELETE") return new Response(null, { status: 204 });
      if (url === "/api/me") return jsonResponse({ id: 1, email: "pat@example.com", createdAt: "2026-11-01T10:00:00Z" });
      if (url === "/api/privacy") return jsonResponse({ summary: "s", whatWeStore: [], whatWeNeverStore: ["Original PDF files."], retentionDays: 90, retention: "90 days.", yourControls: [] });
      throw new Error("unexpected " + url);
    });
    renderApp("/account", { token: "tok" });
    expect(await screen.findByText("Original PDF files.")).toBeInTheDocument();

    const button = screen.getByRole("button", { name: "Delete my account" });
    expect(button).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Confirm with your password"), "correct-horse");
    await userEvent.click(screen.getByLabelText("I understand this deletes everything."));
    await userEvent.click(button);

    expect(await screen.findByRole("button", { name: "Log in" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Your account and all its data have been deleted.");
    const del = fetch.mock.calls.find(([, init]) => (init as RequestInit).method === "DELETE")!;
    expect(JSON.parse((del[1] as RequestInit).body as string)).toEqual({ password: "correct-horse" });
  });

  it("shows 'Incorrect password'", async () => {
    mockFetch((url, init) => {
      if (init.method === "DELETE") return apiError(403, "Incorrect password");
      if (url === "/api/privacy") return jsonResponse({ summary: "s", whatWeStore: [], whatWeNeverStore: [], retentionDays: 90, retention: "", yourControls: [] });
      return jsonResponse({ id: 1, email: "pat@example.com", createdAt: "2026-11-01T10:00:00Z" });
    });
    renderApp("/account", { token: "tok" });
    await userEvent.type(await screen.findByLabelText("Confirm with your password"), "nope");
    await userEvent.click(screen.getByLabelText("I understand this deletes everything."));
    await userEvent.click(screen.getByRole("button", { name: "Delete my account" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Incorrect password");
  });
});
