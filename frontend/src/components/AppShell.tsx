import { Avatar, Chip, Separator } from "@heroui/react";
import {
  Activity,
  Boxes,
  Gauge,
  LayoutDashboard,
  LogOut,
  ReceiptText,
  ShoppingBag,
  ServerCog,
  ShieldCheck,
  Users,
  WalletCards,
} from "lucide-react";
import type { ReactNode } from "react";

import { logout, type UserProfile } from "../api/auth";
import { BrandMark } from "./BrandMark";

interface AppShellProps {
  active: string;
  area: "console" | "admin";
  children: ReactNode;
  user: UserProfile;
}

const consoleNavigation = [
  { key: "overview", label: "套餐概览", href: "/console", icon: Gauge },
  { key: "plans", label: "套餐与购买", href: "/console/plans", icon: WalletCards },
  { key: "orders", label: "订单记录", href: "/console/orders", icon: ShoppingBag },
  { key: "usage", label: "用量明细", href: "/console/usage", icon: Activity },
  { key: "ledger", label: "额度流水", href: "/console/ledger", icon: ReceiptText },
];

const adminNavigation = [
  { key: "admin", label: "运营总览", href: "/admin", icon: LayoutDashboard },
  { key: "users", label: "用户与角色", href: "/admin", icon: Users },
  { key: "billing", label: "套餐与计费", href: "/admin/billing", icon: WalletCards },
  { key: "models", label: "模型目录", href: "/admin/models", icon: Boxes },
  { key: "providers", label: "模型供应商", href: "/admin/providers", icon: ServerCog },
  { key: "gateway", label: "网关诊断", href: "/admin", icon: ServerCog },
];

export function AppShell({ active, area, children, user }: AppShellProps) {
  const navigation = area === "console" ? consoleNavigation : adminNavigation;
  const homeHref = area === "console" ? "/console" : "/admin";

  return (
    <div className="min-h-screen bg-background text-foreground lg:grid lg:grid-cols-[248px_minmax(0,1fr)]">
      <aside className="sticky top-0 hidden h-screen flex-col border-r border-border bg-surface px-4 py-5 lg:flex">
        <a className="flex items-center gap-3 px-2" href={homeHref}>
          <BrandMark />
          <span className="flex flex-col">
            <strong className="text-sm font-semibold">Lumora</strong>
            <small className="text-xs text-muted">
              {area === "console" ? "Cloud Console" : "Admin Console"}
            </small>
          </span>
        </a>

        <p className="mb-2 mt-8 px-3 text-xs font-medium text-muted">
          {area === "console" ? "用户控制台" : "管理控制台"}
        </p>

        <nav className="flex flex-col gap-1" aria-label={area === "console" ? "用户控制台" : "管理端"}>
          {navigation.map((item) => {
            const Icon = item.icon;
            return (
              <a
                className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors ${
                  active === item.key
                    ? "bg-default font-medium text-foreground"
                    : "text-muted hover:bg-default/60 hover:text-foreground"
                }`}
                href={item.href}
                key={item.key}
              >
                <Icon size={18} strokeWidth={1.8} />
                <span>{item.label}</span>
              </a>
            );
          })}
        </nav>

        <div className="mt-auto space-y-4">
          <Separator />
          <Chip color="success" size="sm" variant="soft">云端服务正常</Chip>
          <a
            className="flex items-center gap-2 px-2 text-sm text-muted hover:text-foreground"
            href="/login"
            onClick={(event) => {
              event.preventDefault();
              void logout().finally(() => window.location.replace("/login"));
            }}
          >
            <LogOut size={16} /> 退出当前会话
          </a>
        </div>
      </aside>

      <section className="min-w-0">
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-border bg-background/90 px-4 backdrop-blur md:px-8">
          <a className="flex items-center gap-2 lg:hidden" href={homeHref}>
            <BrandMark />
            <strong className="text-sm">Lumora</strong>
          </a>
          <div className="hidden items-center gap-2 text-sm text-muted lg:flex">
            <ShieldCheck size={16} /> {area === "console" ? "独立网页会话" : "管理员权限"}
          </div>
          <div className="flex items-center gap-4">
            {area === "admin" ? (
              <a className="text-sm text-muted hover:text-foreground" href="/console">查看用户端</a>
            ) : user.roles.includes("ADMIN") ? (
              <a className="text-sm text-muted hover:text-foreground" href="/admin">查看运营端</a>
            ) : null}
            <Avatar size="sm">
              <Avatar.Fallback>{initials(user.displayName)}</Avatar.Fallback>
            </Avatar>
          </div>
        </header>
        <main className="mx-auto w-full max-w-7xl px-4 py-8 md:px-8 md:py-10">{children}</main>
      </section>
    </div>
  );
}

function initials(displayName: string): string {
  return Array.from(displayName.trim()).slice(0, 2).join("").toUpperCase() || "LU";
}
