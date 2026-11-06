import { NavLink, Outlet, Navigate, useLocation } from "react-router";
import { useAuth } from "../auth/useAuth";

/** Pages that need a login. Without a token (or after a 401), go to the login screen. */
export function RequireAuth() {
  const { token } = useAuth();
  const location = useLocation();
  if (!token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Layout />;
}

const LINKS = [
  { to: "/resumes", label: "Resumes" },
  { to: "/analyze", label: "Analyze" },
  { to: "/history", label: "History" },
  { to: "/account", label: "Account" },
];

function Layout() {
  const { logOut } = useAuth();
  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <NavLink to="/resumes" className="brand">
            <img src="/favicon.svg" alt="" width="24" height="24" />
            Resume Matcher
          </NavLink>
          <nav className="nav" aria-label="Main">
            {LINKS.map((link) => (
              <NavLink key={link.to} to={link.to} className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}>
                {link.label}
              </NavLink>
            ))}
          </nav>
          <button type="button" className="button ghost small" onClick={() => logOut("You've been logged out.")}>
            Log out
          </button>
        </div>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </div>
  );
}
