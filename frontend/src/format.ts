/**
 * Formatadores pt-PT, única cópia dos `Intl.NumberFormat`/`Intl.DateTimeFormat` do
 * frontend. DocumentsTable e o dashboard consomem os mesmos — nunca três cópias do
 * mesmo formatador à solta em ficheiros.
 */

const euroFormatter = new Intl.NumberFormat("pt-PT", {
  style: "currency",
  currency: "EUR",
});

const amountFormatter = new Intl.NumberFormat("pt-PT", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const percentFormatter = new Intl.NumberFormat("pt-PT", {
  style: "percent",
  maximumFractionDigits: 1,
});

const dateFormatter = new Intl.DateTimeFormat("pt-PT");

/** 1234.5 → "1.234,50 €" */
export function formatEuro(amount: number): string {
  return euroFormatter.format(amount);
}

/** `null` → "—"; (1234.5, "EUR") → "1.234,50 EUR" — células de tabela com moeda ao lado. */
export function formatAmount(amount: number | null, currency: string | null): string {
  if (amount === null) {
    return "—";
  }
  const formatted = amountFormatter.format(amount);
  return currency !== null ? `${formatted} ${currency}` : formatted;
}

/** 0.75 → "75 %"; `null` → "—" (nunca 0 % sobre nada). */
export function formatPercent(rate: number | null): string {
  if (rate === null) {
    return "—";
  }
  return percentFormatter.format(rate);
}

/**
 * `isoDate` é um `LocalDate` — uma data de calendário, sem hora nem fuso. Passá-la ao
 * `new Date()` faria dela meia-noite UTC, que a oeste de Greenwich se lê como o dia
 * anterior. Constrói-se a data no fuso local a partir dos três números.
 */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  if (year === undefined || month === undefined || day === undefined) {
    return isoDate;
  }
  return dateFormatter.format(new Date(year, month - 1, day));
}

/** 45 → "45 s"; 90 → "1 min 30 s"; 3660 → "1 h 1 min". Tempo médio de revisão. */
export function formatDuration(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3_600);
  const minutes = Math.floor((totalSeconds % 3_600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return minutes > 0 ? `${hours} h ${minutes} min` : `${hours} h`;
  }
  if (minutes > 0) {
    return seconds > 0 ? `${minutes} min ${seconds} s` : `${minutes} min`;
  }
  return `${String(seconds)} s`;
}