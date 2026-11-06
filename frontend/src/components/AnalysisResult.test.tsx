import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Analysis } from "../api/types";
import { AnalysisResult } from "./AnalysisResult";

const analysis: Analysis = {
  id: 7,
  resumeId: 3,
  createdAt: "2026-11-06T10:00:00Z",
  engine: "rule-based-v1",
  matchScore: 61,
  summary: "Good match (61/100). Your resume shows 8 of 11 required skills and 1 of 4 preferred skills.",
  scoreBreakdown: {
    formula: "score = 70% required-skill coverage + 20% preferred-skill coverage + 10% keyword coverage.",
    components: [
      { name: "Required skills", weight: 70, matched: 8, total: 11, coveragePercent: 73, points: 50.9, explanation: "8 of 11 required skills found (73%) = 50.9 of 70 points" },
      { name: "Preferred skills", weight: 20, matched: 1, total: 4, coveragePercent: 25, points: 5, explanation: "1 of 4 preferred skills found (25%) = 5 of 20 points" },
      { name: "Keywords", weight: 10, matched: 8, total: 15, coveragePercent: 53, points: 5.3, explanation: "8 of 15 keywords found (53%) = 5.3 of 10 points" },
    ],
  },
  matchedSkills: [
    { name: "Java", category: "language", importance: "REQUIRED" },
    { name: "JPA", category: "framework", importance: "REQUIRED", impliedBy: "Hibernate" },
    {
      name: "AWS",
      category: "cloud",
      importance: "PREFERRED",
      subSkills: [
        { name: "Amazon EC2", category: "cloud", found: false },
        { name: "Amazon S3", category: "cloud", found: true },
      ],
    },
  ],
  missingRequiredSkills: [{ name: "Docker", category: "devops" }],
  missingPreferredSkills: [{ name: "Azure", category: "cloud", subSkills: [{ name: "Azure DevOps", category: "devops" }] }],
  matchedKeywords: ["payments", "latency"],
  missingKeywords: ["scalable"],
  tips: ["If you've used Docker, add it to your skills section.", "Put the most relevant experience first."],
};

describe("AnalysisResult", () => {
  it("shows the score, band and summary prominently", () => {
    render(<AnalysisResult analysis={analysis} resumeName="cv.pdf" />);
    expect(screen.getByRole("img", { name: "Match score 61 out of 100: Good match" })).toBeInTheDocument();
    expect(screen.getByText(analysis.summary)).toBeInTheDocument();
    expect(screen.getByText(/cv\.pdf/)).toBeInTheDocument();
  });

  it("shows the breakdown with points and explanations", () => {
    render(<AnalysisResult analysis={analysis} />);
    expect(screen.getByText("50.9 / 70")).toBeInTheDocument();
    expect(screen.getByText("8 of 15 keywords found (53%) = 5.3 of 10 points")).toBeInTheDocument();
  });

  it("labels implied skills and lists group items with found/not found", () => {
    render(<AnalysisResult analysis={analysis} />);
    expect(screen.getByText("implied by Hibernate")).toBeInTheDocument();
    const items = within(screen.getByRole("list", { name: "AWS includes" })).getAllByRole("listitem");
    expect(items.map((li) => li.textContent)).toEqual(["✗ Amazon EC2 (not found)", "✓ Amazon S3 (found)"]);
  });

  it("shows missing skills as chips, keywords and tips", () => {
    render(<AnalysisResult analysis={analysis} />);
    expect(screen.getByText("Docker")).toBeInTheDocument();
    expect(screen.getByText("any of: Azure DevOps")).toBeInTheDocument();
    expect(screen.getByText("latency")).toBeInTheDocument();
    expect(screen.getByText("scalable")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem").some((li) => li.textContent === analysis.tips[0])).toBe(true);
  });
});
