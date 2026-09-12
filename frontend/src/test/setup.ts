import "@testing-library/jest-dom/vitest";

import { afterAll, afterEach, beforeAll } from "vitest";
import { setupServer } from "msw/node";

import { clearSession } from "../api/client";
import { handlers } from "./handlers";

// jsdom não implementa matchMedia; o Mantine (esquema de cor, breakpoints) precisa dele.
function fakeMatchMedia(query: string): MediaQueryList {
  return {
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  } as MediaQueryList;
}

if (typeof window.matchMedia !== "function") {
  Object.defineProperty(window, "matchMedia", { writable: true, value: fakeMatchMedia });
}

// jsdom também não implementa ResizeObserver, usado pelo Table.ScrollContainer do Mantine.
class FakeResizeObserver {
  observe(): void {
    // no-op
  }
  unobserve(): void {
    // no-op
  }
  disconnect(): void {
    // no-op
  }
}

if (typeof window.ResizeObserver === "undefined") {
  Object.defineProperty(window, "ResizeObserver", { writable: true, value: FakeResizeObserver });
}

// jsdom não implementa scrollIntoView, que o Combobox do Mantine chama ao destacar uma opção.
if (typeof window.HTMLElement.prototype.scrollIntoView !== "function") {
  window.HTMLElement.prototype.scrollIntoView = () => undefined;
}

export const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
  server.resetHandlers();
  // client.ts guarda o token de acesso em estado de módulo, partilhado entre todos os
  // testes do mesmo ficheiro — sem isto, uma sessão criada num teste vaza para o seguinte.
  clearSession();
});
afterAll(() => server.close());
