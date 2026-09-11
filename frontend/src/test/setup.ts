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

// jsdom não implementa scrollIntoView, usado pelo Select/Combobox do Mantine para
// posicionar a opção destacada — sem isto, cada abertura degrada para segundos de trabalho.
if (typeof window.HTMLElement.prototype.scrollIntoView !== "function") {
  window.HTMLElement.prototype.scrollIntoView = () => undefined;
}

// jsdom também não implementa requestAnimationFrame nem IntersectionObserver. O
// posicionamento do Combobox (floating-ui, usado pelo Select) recorre a um destes para
// saber quando parar de reajustar a posição; sem eles, cai num loop de setTimeout que
// nunca termina e cada teste com um Select demora dezenas de segundos.
if (typeof window.requestAnimationFrame !== "function") {
  window.requestAnimationFrame = (callback: FrameRequestCallback): number =>
    window.setTimeout(() => callback(Date.now()), 16);
  window.cancelAnimationFrame = (handle: number): void => window.clearTimeout(handle);
}

class FakeIntersectionObserver {
  observe(): void {
    // no-op
  }
  unobserve(): void {
    // no-op
  }
  disconnect(): void {
    // no-op
  }
  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
}

if (typeof window.IntersectionObserver === "undefined") {
  Object.defineProperty(window, "IntersectionObserver", { writable: true, value: FakeIntersectionObserver });
}

// jsdom devolve sempre um retangulo a zero; o floating-ui (posicionamento do Combobox)
// pode entrar num ciclo de reajuste contínuo ao tentar posicionar um elemento com
// tamanho zero. Um valor fixo não nulo evita esse ciclo.
window.HTMLElement.prototype.getBoundingClientRect = () => ({
  width: 100,
  height: 40,
  top: 0,
  left: 0,
  right: 100,
  bottom: 40,
  x: 0,
  y: 0,
  toJSON() {
    return this;
  },
});

export const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
  server.resetHandlers();
  // client.ts guarda o token de acesso em estado de módulo, partilhado entre todos os
  // testes do mesmo ficheiro — sem isto, uma sessão criada num teste vaza para o seguinte.
  clearSession();
});
afterAll(() => server.close());
