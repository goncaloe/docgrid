import { NavLink, Stack } from "@mantine/core";
import { IconChartBar, IconFileUpload, IconFiles, IconListCheck } from "@tabler/icons-react";
import { NavLink as RouterNavLink, useLocation } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import { ReviewQueueBadge } from "./ReviewQueueBadge";

const REVIEW_ROLES = ["FINANCE", "MANAGER", "ADMIN"];

export function NavLinks() {
  const { session } = useAuth();
  const location = useLocation();
  const canReview = session !== null && REVIEW_ROLES.includes(session.role);

  return (
    <Stack gap="xs">
      <NavLink
        component={RouterNavLink}
        to="/documents"
        label="Documentos"
        leftSection={<IconFiles size={18} />}
        active={location.pathname.startsWith("/documents")}
      />
      <NavLink
        component={RouterNavLink}
        to="/upload"
        label="Submeter"
        leftSection={<IconFileUpload size={18} />}
        active={location.pathname === "/upload"}
      />
      {canReview && (
        <>
          <NavLink
            component={RouterNavLink}
            to="/dashboard"
            label="Dashboard"
            leftSection={<IconChartBar size={18} />}
            active={location.pathname === "/dashboard"}
          />
        </>
      )}
      {canReview && (
        <NavLink
          component={RouterNavLink}
          to="/review-queue"
          label="Fila de revisão"
          leftSection={<IconListCheck size={18} />}
          active={location.pathname === "/review-queue"}
          rightSection={<ReviewQueueBadge />}
        />
      )}
    </Stack>
  );
}
