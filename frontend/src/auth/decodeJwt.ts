export interface AccessTokenClaims {
  userId: string;
  organizationId: string;
  role: string;
}

/**
 * Decodifica as claims `sub`/`org`/`role` de um access token JWT, sem verificar a
 * assinatura — a verificação é sempre feita pelo backend. Serve apenas para o frontend
 * saber quem está autenticado e que rotas mostrar.
 */
export function decodeJwt(token: string): AccessTokenClaims {
  const parts = token.split(".");
  if (parts.length !== 3 || parts[1] === undefined) {
    throw new Error("Token JWT inválido");
  }
  const payload = base64UrlDecode(parts[1]);
  let claims: Record<string, unknown>;
  try {
    claims = JSON.parse(payload) as Record<string, unknown>;
  } catch {
    throw new Error("Token JWT inválido");
  }
  const sub = claims["sub"];
  const org = claims["org"];
  const role = claims["role"];
  if (typeof sub !== "string" || typeof org !== "string" || typeof role !== "string") {
    throw new Error("Token JWT sem as claims esperadas");
  }
  return { userId: sub, organizationId: org, role };
}

function base64UrlDecode(value: string): string {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
  return decodeURIComponent(
    atob(padded)
      .split("")
      .map((c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
      .join(""),
  );
}
