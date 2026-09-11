import { apiFetch, clearSession, getStoredRefreshToken, setSession } from "./client";
import type { LoginRequest, TokenResponse } from "./types";

export async function login(credentials: LoginRequest): Promise<void> {
  const tokens = await apiFetch<TokenResponse>("/api/auth/login", {
    method: "POST",
    body: credentials,
  });
  setSession(tokens, credentials.email);
}

export async function logout(): Promise<void> {
  const refreshToken = getStoredRefreshToken();
  if (refreshToken !== null) {
    try {
      await apiFetch<void>("/api/auth/logout", { method: "POST", body: { refreshToken } });
    } catch {
      // Sessão local é limpa de qualquer forma; o servidor pode já ter revogado o token.
    }
  }
  clearSession();
}
