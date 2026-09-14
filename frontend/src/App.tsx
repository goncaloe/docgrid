import { Navigate, Route, Routes } from "react-router-dom";

import { RequireAuth } from "./auth/RequireAuth";
import { RequireRole } from "./auth/RequireRole";
import { LoginPage } from "./features/auth/LoginPage";
import { DashboardPage } from "./features/dashboard/DashboardPage";
import { DocumentsListPage } from "./features/documents/DocumentsListPage";
import { ExportsPage } from "./features/exports/ExportsPage";
import { ReviewPage } from "./features/review/ReviewPage";
import { ReviewQueuePage } from "./features/review/ReviewQueuePage";
import { UploadPage } from "./features/upload/UploadPage";
import { AppShellLayout } from "./layout/AppShellLayout";

const REVIEW_ROLES = ["FINANCE", "MANAGER", "ADMIN"] as const;

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/documents"
        element={
          <RequireAuth>
            <AppShellLayout>
              <DocumentsListPage />
            </AppShellLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/upload"
        element={
          <RequireAuth>
            <AppShellLayout>
              <UploadPage />
            </AppShellLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/dashboard"
        element={
          <RequireAuth>
            <AppShellLayout>
              <RequireRole allowed={REVIEW_ROLES}>
                <DashboardPage />
              </RequireRole>
            </AppShellLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/exports"
        element={
          <RequireAuth>
            <AppShellLayout>
              <RequireRole allowed={REVIEW_ROLES}>
                <ExportsPage />
              </RequireRole>
            </AppShellLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/review-queue"
        element={
          <RequireAuth>
            <AppShellLayout>
              <RequireRole allowed={REVIEW_ROLES}>
                <ReviewQueuePage />
              </RequireRole>
            </AppShellLayout>
          </RequireAuth>
        }
      />
      <Route
        path="/review/:id"
        element={
          <RequireAuth>
            <AppShellLayout>
              <RequireRole allowed={REVIEW_ROLES}>
                <ReviewPage />
              </RequireRole>
            </AppShellLayout>
          </RequireAuth>
        }
      />
      <Route path="/" element={<Navigate to="/documents" replace />} />
      <Route path="*" element={<Navigate to="/documents" replace />} />
    </Routes>
  );
}
