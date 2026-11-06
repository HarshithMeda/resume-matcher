import { Navigate, Route, Routes } from "react-router";
import { RequireAuth } from "./components/Layout";
import { AccountPage } from "./pages/AccountPage";
import { AnalyzePage } from "./pages/AnalyzePage";
import { LoginPage, RegisterPage } from "./pages/AuthPages";
import { HistoryPage } from "./pages/HistoryPage";
import { NotFoundPage, PublicPrivacyPage } from "./pages/PublicPages";
import { ResultsPage } from "./pages/ResultsPage";
import { ResumesPage } from "./pages/ResumesPage";

// Keep this list in sync with SpaController on the backend (it serves index.html for these paths).
export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/privacy" element={<PublicPrivacyPage />} />
      <Route element={<RequireAuth />}>
        <Route path="/" element={<Navigate to="/resumes" replace />} />
        <Route path="/resumes" element={<ResumesPage />} />
        <Route path="/analyze" element={<AnalyzePage />} />
        <Route path="/results/:id" element={<ResultsPage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/account" element={<AccountPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
