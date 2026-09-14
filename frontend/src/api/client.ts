import type { ProblemDetail, TokenResponse } from "./types";

const REFRESH_TOKEN_KEY = "docgrid.refreshToken";
const USER_EMAIL_KEY = "docgrid.userEmail";

/**
 * Estado de sessão em memória. O access token nunca é persistido — vive só aqui — para
 * reduzir a janela de exposição a XSS; o refresh token, de vida mais longa, tem de
 * sobreviver a um F5 e por isso vai para `localStorage` (o backend não oferece cookie
 * `httpOnly`).
 */
let accessToken: string | null = null;
let accessTokenExpiresAt: string | null = null;
let refreshTimer: ReturnType<typeof setTimeout> | null = null;
let refreshInFlight: Promise<string | null> | null = null;

type SessionListener = (accessToken: string | null) => void;
const listeners = new Set<SessionListener>();

export function onSessionChange(listener: SessionListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function notifyListeners() {
  for (const listener of listeners) {
    listener(accessToken);
  }
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function getStoredRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function getStoredUserEmail(): string | null {
  return localStorage.getItem(USER_EMAIL_KEY);
}

export function setSession(tokens: TokenResponse, email: string): void {
  accessToken = tokens.accessToken;
  accessTokenExpiresAt = tokens.accessTokenExpiresAt;
  localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  localStorage.setItem(USER_EMAIL_KEY, email);
  scheduleProactiveRefresh();
  notifyListeners();
}

export function clearSession(): void {
  accessToken = null;
  accessTokenExpiresAt = null;
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_EMAIL_KEY);
  if (refreshTimer !== null) {
    clearTimeout(refreshTimer);
    refreshTimer = null;
  }
  notifyListeners();
}

/**
 * Chamada uma vez no arranque da aplicação: se houver um refresh token de uma sessão
 * anterior mas nenhum access token em memória (recarregámos a página), tenta renová-lo
 * antes de decidir se há sessão. Sem isto, um F5 mandaria sempre para `/login` mesmo com
 * uma sessão válida.
 */
export function initializeSession(): Promise<void> {
  if (getAccessToken() !== null || getStoredRefreshToken() === null) {
    return Promise.resolve();
  }
  return refreshSession().then(() => undefined);
}

/** Pede um novo access token com o refresh token guardado, uma única vez de cada vez. */
function refreshSession(): Promise<string | null> {
  if (refreshInFlight !== null) {
    return refreshInFlight;
  }
  const storedRefreshToken = getStoredRefreshToken();
  if (storedRefreshToken === null) {
    return Promise.resolve(null);
  }
  const email = getStoredUserEmail() ?? "";
  refreshInFlight = fetch("/api/auth/refresh", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ refreshToken: storedRefreshToken }),
  })
    .then(async (response) => {
      if (!response.ok) {
        clearSession();
        return null;
      }
      const tokens = (await response.json()) as TokenResponse;
      setSession(tokens, email);
      return tokens.accessToken;
    })
    .catch(() => {
      clearSession();
      return null;
    })
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

function scheduleProactiveRefresh(): void {
  if (refreshTimer !== null) {
    clearTimeout(refreshTimer);
    refreshTimer = null;
  }
  if (accessTokenExpiresAt === null) {
    return;
  }
  const msUntilExpiry = new Date(accessTokenExpiresAt).getTime() - Date.now();
  const msUntilRefresh = Math.max(msUntilExpiry - 60_000, 5_000);
  refreshTimer = setTimeout(() => {
    void refreshSession();
  }, msUntilRefresh);
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `Pedido falhou com o estado ${String(status)}`);
    this.status = status;
    this.problem = problem;
  }
}

interface ApiFetchOptions {
  method?: string;
  body?: unknown;
  signal?: AbortSignal;
  /**
   * Recolhe o estado HTTP da resposta final (depois da nova tentativa de refresh, se a
   * houve). Útil quando o significado da resposta depende do status — p.ex. um POST que
   * devolve 201 quando cria e 200 quando já existia.
   */
  onStatus?: (status: number) => void;
}

/**
 * Wrapper de `fetch` que junta o access token, e num 401 tenta um único refresh antes de
 * repetir o pedido original. Se o refresh também falhar, a sessão é limpa e o erro sobe
 * para quem chamou tratar (normalmente redirecionando para `/login`).
 */
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const response = await performFetch(path, options, accessToken);
  if (response.status !== 401) {
    options.onStatus?.(response.status);
    return parseResponse<T>(response);
  }
  const refreshedToken = await refreshSession();
  if (refreshedToken === null) {
    throw new ApiError(401, null);
  }
  const retried = await performFetch(path, options, refreshedToken);
  options.onStatus?.(retried.status);
  return parseResponse<T>(retried);
}

async function performFetch(path: string, options: ApiFetchOptions, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (token !== null) {
    headers["authorization"] = `Bearer ${token}`;
  }
  return fetch(path, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  });
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  let parsed: unknown;
  try {
    parsed = text.length > 0 ? JSON.parse(text) : undefined;
  } catch {
    parsed = undefined;
  }
  if (!response.ok) {
    throw new ApiError(response.status, parsed as ProblemDetail);
  }
  return parsed as T;
}
