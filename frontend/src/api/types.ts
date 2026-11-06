// Shapes of the JSON the backend returns. Kept in sync by hand with the Java records.

export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface User {
  id: number;
  email: string;
  createdAt: string;
}

export interface ResumeSummary {
  id: number;
  fileName: string;
  characterCount: number;
  createdAt: string;
}

export interface ResumeDetail extends ResumeSummary {
  extractedText: string;
}

export type Importance = "REQUIRED" | "PREFERRED";

export interface Skill {
  name: string;
  category: string;
}

export interface SubSkill {
  name: string;
  category: string;
  found: boolean;
  impliedBy?: string;
}

export interface MatchedSkill {
  name: string;
  category: string;
  importance: Importance;
  impliedBy?: string;
  /** Group items ("AWS (EC2, S3)") or, when eitherOr is true, the options ("Power BI or Tableau"). */
  subSkills?: SubSkill[];
  eitherOr?: boolean;
}

export interface MissingSkill {
  name: string;
  category: string;
  subSkills?: Skill[];
  eitherOr?: boolean;
}

export interface ScoreComponent {
  name: string;
  weight: number;
  matched: number;
  total: number;
  coveragePercent: number;
  points: number;
  explanation: string;
}

export interface ScoreBreakdown {
  formula: string;
  components: ScoreComponent[];
}

export interface Analysis {
  id: number;
  resumeId: number;
  createdAt: string;
  engine: string;
  matchScore: number;
  summary: string;
  scoreBreakdown: ScoreBreakdown;
  matchedSkills: MatchedSkill[];
  missingRequiredSkills: MissingSkill[];
  missingPreferredSkills: MissingSkill[];
  matchedKeywords: string[];
  missingKeywords: string[];
  tips: string[];
}

export interface AnalysisListItem {
  id: number;
  resumeId: number;
  matchScore: number;
  summary: string;
  createdAt: string;
}

export interface PrivacyNotice {
  summary: string;
  whatWeStore: string[];
  whatWeNeverStore: string[];
  retentionDays: number;
  retention: string;
  yourControls: string[];
}
