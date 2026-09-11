import { Navigate } from "react-router-dom";

import type { UserRole } from "../api/types";
import { AccessDenied } from "../layout/AccessDenied";
import { useAuth } from "./AuthContext";

interface RequireRoleProps {
  allowed: readonly UserRole[];
  children: React.ReactNode;
}

/**
 * Guarda de rota por papel. Um utilizador sem sessão nenhuma vai para `/login`; um
 * utilizador autenticado mas sem o papel exigido vê "sem permissão" sem chegar a fazer
 * qualquer pedido à API — o critério "EMPLOYEE não chega à fila de revisão nem por URL
 * direto" cumpre-se aqui, antes de qualquer chamada de rede.
 */
export function RequireRole({ allowed, children }: RequireRoleProps) {
  const { session, isInitializing } = useAuth();

  if (isInitializing) {
    return null;
  }

  if (session === null) {
    return <Navigate to="/login" replace />;
  }

  if (!allowed.includes(session.role)) {
    return <AccessDenied />;
  }

  return <>{children}</>;
}
