import { MantineProvider } from "@mantine/core";
import { Notifications } from "@mantine/notifications";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { ReactElement, ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";

import { AuthProvider } from "../auth/AuthContext";

function newQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
}

export function renderWithProviders(
  ui: ReactElement,
  { route = "/", withNotifications = false }: { route?: string; withNotifications?: boolean } = {},
) {
  const queryClient = newQueryClient();

  return render(
    <MantineProvider>
      {withNotifications && <Notifications />}
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>
          <AuthProvider>{ui}</AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  );
}

/** O mínimo para um `renderHook` de um hook que fala com o TanStack Query. */
export function queryWrapper() {
  const queryClient = newQueryClient();
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}
