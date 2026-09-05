import { lazy, Suspense, useEffect, useState, type ReactNode } from "react";

import { onSessionInvalidated, restoreSession, type UserProfile } from "../api/auth";
import { AppShell } from "../components/AppShell";
import { resolveRoute } from "./routes";

const LoginPage = lazy(() => import("../pages/auth/LoginPage").then((module) => ({ default: module.LoginPage })));
const AdminOverviewPage = lazy(() => import("../pages/admin/AdminOverviewPage").then((module) => ({ default: module.AdminOverviewPage })));
const BillingManagementPage = lazy(() => import("../pages/admin/BillingManagementPage").then((module) => ({ default: module.BillingManagementPage })));
const ModelCatalogPage = lazy(() => import("../pages/admin/ModelCatalogPage").then((module) => ({ default: module.ModelCatalogPage })));
const ModelProvidersPage = lazy(() => import("../pages/admin/ModelProvidersPage").then((module) => ({ default: module.ModelProvidersPage })));
const UserManagementPage = lazy(() => import("../pages/admin/UserManagementPage").then((module) => ({ default: module.UserManagementPage })));
const AdminWalletsPage = lazy(() => import("../pages/admin/AdminWalletsPage").then((module) => ({ default: module.AdminWalletsPage })));
const BillingReconciliationPage = lazy(() => import("../pages/admin/BillingReconciliationPage").then((module) => ({ default: module.BillingReconciliationPage })));
const GatewayDiagnosticsPage = lazy(() => import("../pages/admin/GatewayDiagnosticsPage").then((module) => ({ default: module.GatewayDiagnosticsPage })));
const ConsoleOverviewPage = lazy(() => import("../pages/console/ConsoleOverviewPage").then((module) => ({ default: module.ConsoleOverviewPage })));
const BillingHistoryPage = lazy(() => import("../pages/console/BillingHistoryPage").then((module) => ({ default: module.BillingHistoryPage })));
const PlansPage = lazy(() => import("../pages/console/PlansPage").then((module) => ({ default: module.PlansPage })));
const PurchaseOrdersPage = lazy(() => import("../pages/console/PurchaseOrdersPage").then((module) => ({ default: module.PurchaseOrdersPage })));
const WalletPage = lazy(() => import("../pages/console/WalletPage").then((module) => ({ default: module.WalletPage })));

export function App() {
  const route = resolveRoute(window.location.pathname);
  const [status, setStatus] = useState<"loading" | "authenticated" | "anonymous" | "error" | "changed">("loading");
  const [user, setUser] = useState<UserProfile | null>(null);

  useEffect(() => onSessionInvalidated(() => {
    setUser(null);
    setStatus("changed");
  }), []);

  useEffect(() => {
    let active = true;
    restoreSession()
      .then((profile) => {
        if (!active) return;
        setUser(profile);
        setStatus(profile ? "authenticated" : "anonymous");
      })
      .catch(() => {
        if (active) setStatus("error");
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (status === "anonymous" && route !== "login") {
      const next = `${window.location.pathname}${window.location.search}`;
      window.location.replace(`/login?next=${encodeURIComponent(next)}`);
    }
    if (status === "authenticated" && route === "login") {
      window.location.replace(safeNextPath() ?? "/console");
    }
    if (status === "authenticated" && route.startsWith("admin-") && !user?.roles.includes("ADMIN")) {
      window.location.replace("/console");
    }
  }, [route, status, user]);

  if (status === "loading") return <SessionMessage message="正在恢复登录状态…" />;
  if (status === "changed") return <div className="grid min-h-screen place-content-center gap-4 text-center"><p>登录状态已变化，请确认账号后重新操作。</p><button onClick={() => window.location.reload()} type="button">刷新并确认账号</button></div>;
  if (status === "error") return <SessionMessage message="暂时无法连接云端服务，请稍后刷新页面。" />;

  if (route === "login") {
    return (
      <PageSuspense>
        <LoginPage onAuthenticated={(profile) => {
          setUser(profile);
          setStatus("authenticated");
        }} />
      </PageSuspense>
    );
  }

  if (!user) return <SessionMessage message="正在跳转到登录页…" />;

  if (route === "admin-overview") {
    return (
      <AppShell active="admin" area="admin" user={user}>
        <PageSuspense><AdminOverviewPage /></PageSuspense>
      </AppShell>
    );
  }

  if (route === "admin-models") {
    return (
      <AppShell active="models" area="admin" user={user}>
        <PageSuspense><ModelCatalogPage /></PageSuspense>
      </AppShell>
    );
  }

  if (route === "admin-users") {
    return <AppShell active="users" area="admin" user={user}><PageSuspense><UserManagementPage /></PageSuspense></AppShell>;
  }

  if (route === "admin-wallets") {
    return <AppShell active="wallets" area="admin" user={user}><PageSuspense><AdminWalletsPage /></PageSuspense></AppShell>;
  }

  if (route === "admin-reconciliation") {
    return <AppShell active="reconciliation" area="admin" user={user}><PageSuspense><BillingReconciliationPage /></PageSuspense></AppShell>;
  }

  if (route === "admin-gateway") {
    return <AppShell active="gateway" area="admin" user={user}><PageSuspense><GatewayDiagnosticsPage /></PageSuspense></AppShell>;
  }

  if (route === "admin-billing") {
    return (
      <AppShell active="billing" area="admin" user={user}>
        <PageSuspense><BillingManagementPage /></PageSuspense>
      </AppShell>
    );
  }

  if (route === "admin-providers") {
    return (
      <AppShell active="providers" area="admin" user={user}>
        <PageSuspense><ModelProvidersPage /></PageSuspense>
      </AppShell>
    );
  }

  if (route === "console-plans") {
    return (
      <AppShell active="plans" area="console" user={user}>
        <PageSuspense><PlansPage /></PageSuspense>
      </AppShell>
    );
  }

  if (route === "console-wallet") {
    return <AppShell active="wallet" area="console" user={user}><PageSuspense><WalletPage /></PageSuspense></AppShell>;
  }

  if (route === "console-orders" || route === "console-order-detail") {
    const orderNo = route === "console-order-detail"
      ? window.location.pathname.split("/").filter(Boolean).at(-1)
      : undefined;
    return (
      <AppShell active="orders" area="console" user={user}>
        <PageSuspense><PurchaseOrdersPage orderNo={orderNo} /></PageSuspense>
      </AppShell>
    );
  }

  if (route === "console-usage") {
    return (
      <AppShell active="usage" area="console" user={user}>
        <PageSuspense><BillingHistoryPage mode="usage" /></PageSuspense>
      </AppShell>
    );
  }

  if (route === "console-ledger") {
    return (
      <AppShell active="ledger" area="console" user={user}>
        <PageSuspense><BillingHistoryPage mode="ledger" /></PageSuspense>
      </AppShell>
    );
  }

  return (
    <AppShell active="overview" area="console" user={user}>
      <PageSuspense><ConsoleOverviewPage /></PageSuspense>
    </AppShell>
  );
}

function PageSuspense({ children }: { children: ReactNode }) {
  return <Suspense fallback={<SessionMessage message="正在加载页面…" />}>{children}</Suspense>;
}

function safeNextPath(): string | null {
  const next = new URLSearchParams(window.location.search).get("next");
  return next?.startsWith("/") && !next.startsWith("//") ? next : null;
}

function SessionMessage({ message }: { message: string }) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-4 text-sm text-muted">
      {message}
    </main>
  );
}
