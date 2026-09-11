import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { server } from "../test/setup";
import { EMPLOYEE_ACCESS_TOKEN, tokenResponse } from "../test/handlers";
import { apiFetch, clearSession, setSession } from "./client";

describe("apiFetch", () => {
  beforeEach(() => {
    clearSession();
  });

  it("junta o access token e devolve o corpo em pedidos bem-sucedidos", async () => {
    setSession(tokenResponse(EMPLOYEE_ACCESS_TOKEN), "ana@padaria.pt");
    server.use(
      http.get("/api/ping", ({ request }) => {
        expect(request.headers.get("authorization")).toBe(`Bearer ${EMPLOYEE_ACCESS_TOKEN}`);
        return HttpResponse.json({ ok: true });
      }),
    );

    const result = await apiFetch<{ ok: boolean }>("/api/ping");
    expect(result).toEqual({ ok: true });
  });

  it("num 401, renova a sessão uma vez e repete o pedido original", async () => {
    setSession(tokenResponse("token-expirado"), "ana@padaria.pt");
    let attempt = 0;

    server.use(
      http.get("/api/ping", ({ request }) => {
        attempt += 1;
        const authorization = request.headers.get("authorization");
        if (authorization === "Bearer token-expirado") {
          return HttpResponse.json({ title: "Não autorizado" }, { status: 401 });
        }
        return HttpResponse.json({ ok: true, attempt });
      }),
      http.post("/api/auth/refresh", () => HttpResponse.json(tokenResponse(EMPLOYEE_ACCESS_TOKEN))),
    );

    const result = await apiFetch<{ ok: boolean; attempt: number }>("/api/ping");
    expect(result).toEqual({ ok: true, attempt: 2 });
  });

  it("quando o refresh também falha, o erro sobe e a sessão fica limpa", async () => {
    setSession(tokenResponse("token-expirado"), "ana@padaria.pt");

    server.use(
      http.get("/api/ping", () => HttpResponse.json({ title: "Não autorizado" }, { status: 401 })),
      http.post("/api/auth/refresh", () => HttpResponse.json({ title: "Refresh inválido" }, { status: 401 })),
    );

    await expect(apiFetch("/api/ping")).rejects.toThrow();
  });

  it("pedidos concorrentes durante um refresh não disparam refreshes duplicados", async () => {
    setSession(tokenResponse("token-expirado"), "ana@padaria.pt");
    const refreshCalls = vi.fn();

    server.use(
      http.get("/api/ping", ({ request }) => {
        const authorization = request.headers.get("authorization");
        if (authorization === "Bearer token-expirado") {
          return HttpResponse.json({}, { status: 401 });
        }
        return HttpResponse.json({ ok: true });
      }),
      http.post("/api/auth/refresh", () => {
        refreshCalls();
        return HttpResponse.json(tokenResponse(EMPLOYEE_ACCESS_TOKEN));
      }),
    );

    await Promise.all([apiFetch("/api/ping"), apiFetch("/api/ping")]);
    expect(refreshCalls).toHaveBeenCalledTimes(1);
  });
});
