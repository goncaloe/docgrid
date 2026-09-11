import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

import { login as loginRequest, logout as logoutRequest } from "../api/auth";
import { getAccessToken, getStoredRefreshToken, getStoredUserEmail, initializeSession, onSessionChange } from "../api/client";
import type { LoginRequest, UserRole } from "../api/types";
import { decodeJwt } from "./decodeJwt";

export interface AuthSession {
  userId: string;
  organizationId: string;
  role: UserRole;
  email: string;
}

interface AuthContextValue {
  session: AuthSession | null;
  isInitializing: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function sessionFromAccessToken(token: string | null, email: string | null): AuthSession | null {
  if (token === null) return null;
  try {
    const claims = decodeJwt(token);
    return { userId: claims.userId, organizationId: claims.organizationId, role: claims.role as UserRole, email: email ?? "" };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);

  useEffect(() => {
    setSession(sessionFromAccessToken(getAccessToken(), getStoredUserEmail()));
    const unsubscribe = onSessionChange((token) => {
      setSession(sessionFromAccessToken(token, getStoredUserEmail()));
    });

    void initializeSession().finally(() => {
      setSession(sessionFromAccessToken(getAccessToken(), getStoredUserEmail()));
      setIsInitializing(false);
    });

    return unsubscribe;
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      isInitializing,
      login: async (credentials) => {
        await loginRequest(credentials);
        setSession(sessionFromAccessToken(getAccessToken(), getStoredUserEmail()));
      },
      logout: async () => {
        await logoutRequest();
        setSession(null);
      },
    }),
    [session, isInitializing],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (context === null) {
    throw new Error("useAuth tem de ser usado dentro de AuthProvider");
  }
  return context;
}

export function hasStoredSession(): boolean {
  return getStoredRefreshToken() !== null;
}
