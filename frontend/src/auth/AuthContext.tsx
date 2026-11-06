import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { SESSION_EXPIRED, setUnauthorizedHandler, tokenStore } from "../api/client";
import { AuthContext } from "./context";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => tokenStore.get());
  const [notice, setNotice] = useState<string | null>(null);

  const logIn = useCallback((newToken: string) => {
    tokenStore.set(newToken);
    setToken(newToken);
    setNotice(null);
  }, []);

  const logOut = useCallback((message?: string) => {
    tokenStore.clear();
    setToken(null);
    setNotice(message ?? null);
  }, []);

  // Any request that gets 401 with our token logs us out; RequireAuth then shows the login screen.
  useEffect(() => {
    setUnauthorizedHandler(() => logOut(SESSION_EXPIRED));
    return () => setUnauthorizedHandler(null);
  }, [logOut]);

  const value = useMemo(
    () => ({ token, notice, logIn, logOut, clearNotice: () => setNotice(null) }),
    [token, notice, logIn, logOut],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
