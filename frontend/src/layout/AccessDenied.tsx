import { Center, Stack, Text, Title } from "@mantine/core";

export function AccessDenied() {
  return (
    <Center h="60vh">
      <Stack align="center" gap="xs">
        <Title order={3}>Sem permissão</Title>
        <Text c="dimmed">Não tens acesso a esta página com o teu papel atual.</Text>
      </Stack>
    </Center>
  );
}
