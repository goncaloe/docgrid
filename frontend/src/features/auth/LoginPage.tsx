import { Alert, Button, Center, Paper, PasswordInput, Stack, TextInput, Title } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { useState } from "react";
import { Navigate, useLocation, useNavigate, type Location } from "react-router-dom";

import { ApiError } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";

interface LocationState {
  from?: Location;
}

export function LoginPage() {
  const { login, session } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      await login({ email, password });
      const state = location.state as LocationState | null;
      navigate(state?.from?.pathname ?? "/documents", { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError("Email ou palavra-passe incorretos.");
      } else {
        setError("Não foi possível entrar. Tenta novamente.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  // Já autenticado: o formulário não tem nada a fazer aqui.
  if (session !== null) {
    return <Navigate to="/documents" replace />;
  }

  return (
    <Center h="100vh" bg="gray.0" p="md">
      <Paper withBorder shadow="sm" p="xl" radius="md" w="100%" maw={360}>
        <Stack gap="md">
          <Title order={2}>DocGrid</Title>
          <form onSubmit={(event) => void handleSubmit(event)}>
            <Stack gap="sm">
              <TextInput
                label="Email"
                type="email"
                required
                value={email}
                onChange={(event) => setEmail(event.currentTarget.value)}
                autoComplete="username"
              />
              <PasswordInput
                label="Palavra-passe"
                required
                value={password}
                onChange={(event) => setPassword(event.currentTarget.value)}
                autoComplete="current-password"
              />
              {error !== null && (
                <Alert color="red" icon={<IconAlertCircle size={16} />}>
                  {error}
                </Alert>
              )}
              <Button type="submit" loading={isSubmitting} fullWidth>
                Entrar
              </Button>
            </Stack>
          </form>
        </Stack>
      </Paper>
    </Center>
  );
}
