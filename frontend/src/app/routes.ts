export type AppRoute =
  | "login"
  | "console-overview"
  | "console-plans"
  | "console-orders"
  | "console-order-detail"
  | "console-usage"
  | "console-ledger"
  | "admin-overview"
  | "admin-billing"
  | "admin-models"
  | "admin-providers";

export function resolveRoute(pathname: string): AppRoute {
  if (pathname.startsWith("/login")) return "login";
  if (pathname.startsWith("/admin/billing")) return "admin-billing";
  if (pathname.startsWith("/admin/providers")) return "admin-providers";
  if (pathname.startsWith("/admin/models")) return "admin-models";
  if (pathname.startsWith("/admin")) return "admin-overview";
  if (pathname.startsWith("/console/usage")) return "console-usage";
  if (pathname.startsWith("/console/ledger")) return "console-ledger";
  if (/^\/console\/orders\/[^/]+\/?$/.test(pathname)) return "console-order-detail";
  if (pathname.startsWith("/console/orders")) return "console-orders";
  if (pathname.startsWith("/console/plans")) return "console-plans";
  return "console-overview";
}
