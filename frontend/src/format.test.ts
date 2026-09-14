import { describe, expect, it } from "vitest";

import { formatAmount, formatDate, formatDuration, formatEuro, formatPercent } from "./format";

/** O formato pt-PT separa os milhares com um espaço estreito (U+00A0/U+202F, segundo a ICU). */
function stripGroupSeparators(value: string): string {
  return value.replace(/[\u00A0\u202F]/g, "");
}

describe("formatadores pt-PT", () => {
  it("formata euros com vírgula decimal e separador de milhares", () => {
    expect(stripGroupSeparators(formatEuro(12345.67))).toBe("12345,67€");
    expect(stripGroupSeparators(formatEuro(1234.5))).toBe("1234,50€");
  });

  it("formata o montante com a moeda ao lado, e «—» quando é null", () => {
    expect(stripGroupSeparators(formatAmount(1234.5, "EUR"))).toBe("1234,50 EUR");
    expect(stripGroupSeparators(formatAmount(12345.67, null))).toBe("12345,67");
    expect(formatAmount(null, "EUR")).toBe("—");
  });

  it("formata a percentagem com vírgula decimal e sem espaço antes de %", () => {
    expect(formatPercent(0.75)).toBe("75%");
    expect(formatPercent(0.3333)).toBe("33,3%");
    expect(formatPercent(null)).toBe("—");
  });

  it("formata uma LocalDate desde ISO sem erro de fuso", () => {
    expect(formatDate("2026-08-20")).toBe("20/08/2026");
  });

  it("formata durações em h/min/s", () => {
    expect(formatDuration(45)).toBe("45 s");
    expect(formatDuration(60)).toBe("1 min");
    expect(formatDuration(90)).toBe("1 min 30 s");
    expect(formatDuration(3600)).toBe("1 h");
    expect(formatDuration(3660)).toBe("1 h 1 min");
  });
});