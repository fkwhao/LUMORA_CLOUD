import {
  Activity,
  Boxes,
  CircleUserRound,
  Gauge,
  LayoutDashboard,
  LogOut,
  ReceiptText,
  ServerCog,
  ShieldCheck,
  Users,
  WalletCards,
} from "lucide-react";
import type { ReactNode } from "react";

import { BrandMark } from "./BrandMark";

interface AppShellProps {
  active: string;
  area: "console" | "admin";
  children: ReactNode;
}

const consoleNavigation = [
  { key: "overview", label: "套餐概览", href: "/console", icon: Gauge },
  { key: "plans", label: "套餐与购买", href: "/console/plans", icon: WalletCards },
  { key: "usage", label: "用量明细", href: "/console", icon: Activity },
  { key: "bills", label: "账单记录", href: "/console", icon: ReceiptText },
];

const adminNavigation = [
  { key: "admin", label: "运营总览", href: "/admin", icon: LayoutDashboard },
  { key: "users", label: "用户与角色", href: "/admin", icon: Users },
  { key: "billing", label: "套餐与计费", href: "/admin", icon: WalletCards },
  { key: "models", label: "模型目录", href: "/admin", icon: Boxes },
  { key: "gateway", label: "网关诊断", href: "/admin", icon: ServerCog },
];

export function AppShell({ active, area, children }: AppShellProps) {
  const navigation = area === "console" ? consoleNavigation : adminNavigation;

  return (
    <div className={`cloud-shell ${area}-shell`}>
      <aside className="cloud-sidebar">
        <a className="cloud-brand" href={area === "console" ? "/console" : "/admin"}>
          <BrandMark />
          <span>
            <strong>Lumora</strong>
            <small>{area === "console" ? "Cloud Console" : "Operations"}</small>
          </span>
        </a>

        <div className="sidebar-context">
          <span>{area === "console" ? "个人空间" : "管理空间"}</span>
          <strong>{area === "console" ? "模型与额度" : "云端运行面"}</strong>
        </div>

        <nav className="cloud-navigation" aria-label={area === "console" ? "用户控制台" : "管理端"}>
          {navigation.map((item) => {
            const Icon = item.icon;
            return (
              <a className={active === item.key ? "active" : undefined} href={item.href} key={item.key}>
                <Icon size={17} strokeWidth={1.7} />
                <span>{item.label}</span>
              </a>
            );
          })}
        </nav>

        <div className="sidebar-foot">
          <div className="service-pulse"><i /> 云端服务正常</div>
          <a href="/login"><LogOut size={15} /> 退出当前会话</a>
        </div>
      </aside>

      <section className="cloud-workspace">
        <header className="cloud-topbar">
          <div className="environment-chip">
            <ShieldCheck size={14} /> {area === "console" ? "独立网页会话" : "管理员权限"}
          </div>
          <div className="topbar-actions">
            <a href={area === "console" ? "/admin" : "/console"}>
              {area === "console" ? "查看运营端" : "查看用户端"}
            </a>
            <span className="topbar-avatar"><CircleUserRound size={18} /></span>
          </div>
        </header>
        <main className="cloud-main">{children}</main>
      </section>
    </div>
  );
}
