import { createContext } from "react";

export interface AuthState {
  token: string | null;
  /** A message for the login screen, e.g. "Your session has expired." */
  notice: string | null;
  logIn: (token: string) => void;
  logOut: (notice?: string) => void;
  clearNotice: () => void;
}

export const AuthContext = createContext<AuthState | null>(null);
