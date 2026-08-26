import { AppShell } from "../components/AppShell";
import { LoginPage } from "../pages/auth/LoginPage";
import { AdminOverviewPage } from "../pages/admin/AdminOverviewPage";
import { ConsoleOverviewPage } from "../pages/console/ConsoleOverviewPage";
import { PlansPage } from "../pages/console/PlansPage";
import { resolveRoute } from "./routes";

export function App() {
  const route = resolveRoute(window.location.pathname);

  if (route === "login") return <LoginPage />;

  if (route === "admin-overview") {
    return (
      <AppShell active="admin" area="admin">
        <AdminOverviewPage />
      </AppShell>
    );
  }

  if (route === "console-plans") {
    return (
      <AppShell active="plans" area="console">
        <PlansPage />
      </AppShell>
    );
  }

  return (
    <AppShell active="overview" area="console">
      <ConsoleOverviewPage />
    </AppShell>
  );
}
