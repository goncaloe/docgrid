import { describe, expect, it } from "vitest";

import { decodeJwt } from "./decodeJwt";

function fakeToken(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

describe("decodeJwt", () => {
  it("decodifica as claims sub/org/role", () => {
    const token = fakeToken({ sub: "user-1", org: "org-1", role: "FINANCE" });
    expect(decodeJwt(token)).toEqual({ userId: "user-1", organizationId: "org-1", role: "FINANCE" });
  });

  it("rejeita um token sem três partes", () => {
    expect(() => decodeJwt("not-a-token")).toThrow();
  });

  it("rejeita um token com payload que não é JSON", () => {
    expect(() => decodeJwt(`header.${btoa("nao-e-json")}.signature`)).toThrow();
  });

  it("rejeita um token sem as claims esperadas", () => {
    const token = fakeToken({ sub: "user-1" });
    expect(() => decodeJwt(token)).toThrow();
  });
});
