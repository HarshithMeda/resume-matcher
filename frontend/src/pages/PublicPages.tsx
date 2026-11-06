import { Link } from "react-router";
import { PrivacySection } from "./AccountPage";

/** The privacy note for people who haven't logged in yet. */
export function PublicPrivacyPage() {
  return (
    <div className="page narrow">
      <p>
        <Link to="/login">← Back to log in</Link>
      </p>
      <PrivacySection />
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="page narrow">
      <h1>Page not found</h1>
      <p>
        <Link to="/resumes">Go to your resumes</Link>
      </p>
    </div>
  );
}
