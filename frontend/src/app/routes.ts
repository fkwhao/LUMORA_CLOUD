export type AppRoute =
  | "login"
  | "console-overview"
  | "console-plans"
  | "admin-overview";

export function resolveRoute(pathname: string): AppRoute {
  if (pathname.startsWith("/login")) return "login";
  if (pathname.startsWith("/admin")) return "admin-overview";
  if (pathname.startsWith("/console/plans")) return "console-plans";
  return "console-overview";
}
