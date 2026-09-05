export type AppRoute =
  | "login"
  | "console-overview"
  | "console-plans"
  | "console-orders"
  | "console-order-detail"
  | "console-usage"
  | "console-ledger"
  | "console-wallet"
  | "admin-overview"
  | "admin-billing"
  | "admin-models"
  | "admin-providers"
  | "admin-users"
  | "admin-wallets"
  | "admin-gateway"
  | "admin-reconciliation";

export function resolveRoute(pathname: string): AppRoute {
  if (pathname.startsWith("/login")) return "login";
  if (pathname.startsWith("/admin/reconciliation")) return "admin-reconciliation";
  if (pathname.startsWith("/admin/billing")) return "admin-billing";
  if (pathname.startsWith("/admin/users")) return "admin-users";
  if (pathname.startsWith("/admin/wallets")) return "admin-wallets";
  if (pathname.startsWith("/admin/gateway")) return "admin-gateway";
  if (pathname.startsWith("/admin/providers")) return "admin-providers";
  if (pathname.startsWith("/admin/models")) return "admin-models";
  if (pathname.startsWith("/admin")) return "admin-overview";
  if (pathname.startsWith("/console/usage")) return "console-usage";
  if (pathname.startsWith("/console/ledger")) return "console-ledger";
  if (pathname.startsWith("/console/wallet")) return "console-wallet";
  if (/^\/console\/orders\/[^/]+\/?$/.test(pathname)) return "console-order-detail";
  if (pathname.startsWith("/console/orders")) return "console-orders";
  if (pathname.startsWith("/console/plans")) return "console-plans";
  return "console-overview";
}
