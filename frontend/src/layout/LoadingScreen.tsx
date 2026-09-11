import { Center, Loader } from "@mantine/core";

export function LoadingScreen() {
  return (
    <Center h="100vh">
      <Loader aria-label="A carregar" />
    </Center>
  );
}
