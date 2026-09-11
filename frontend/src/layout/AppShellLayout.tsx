import { AppShell, Burger, Group, Menu, Text, UnstyledButton } from "@mantine/core";
import { useDisclosure } from "@mantine/hooks";
import { IconChevronDown, IconLogout } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import { NavLinks } from "./NavLinks";

const ROLE_LABELS: Record<string, string> = {
  EMPLOYEE: "Colaborador",
  FINANCE: "Financeiro",
  MANAGER: "Gestor",
  ADMIN: "Administrador",
};

export function AppShellLayout({ children }: { children: ReactNode }) {
  const [opened, { toggle }] = useDisclosure();
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  return (
    <AppShell
      header={{ height: 60 }}
      navbar={{ width: 240, breakpoint: "sm", collapsed: { mobile: !opened } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group>
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <Text fw={700}>DocGrid</Text>
          </Group>
          {session !== null && (
            <Menu shadow="md" width={220}>
              <Menu.Target>
                <UnstyledButton aria-label="Menu do utilizador">
                  <Group gap="xs">
                    <div>
                      <Text size="sm" fw={500}>
                        {session.email}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {ROLE_LABELS[session.role] ?? session.role}
                      </Text>
                    </div>
                    <IconChevronDown size={16} />
                  </Group>
                </UnstyledButton>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Item leftSection={<IconLogout size={16} />} onClick={() => void handleLogout()}>
                  Terminar sessão
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          )}
        </Group>
      </AppShell.Header>
      <AppShell.Navbar p="md">
        <NavLinks />
      </AppShell.Navbar>
      <AppShell.Main>{children}</AppShell.Main>
    </AppShell>
  );
}
