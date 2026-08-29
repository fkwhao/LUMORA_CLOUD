import { Button, Card, Chip } from "@heroui/react";
import {
  Activity,
  Boxes,
  CheckCircle2,
  CircleDollarSign,
  Clock3,
  Database,
  RefreshCw,
  ServerCog,
  Users,
  WalletCards,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { ApiClientError } from "../../api/auth";
import { getBillingStatistics, type BillingStatistics } from "../../api/billing";
import { getCatalogStatistics, type CatalogStatistics } from "../../api/catalog";
import { getUserStatistics, type UserStatistics } from "../../api/users";

type Domain = "users" | "billing" | "catalog";

export function AdminOverviewPage() {
  const [users, setUsers] = useState<UserStatistics | null>(null);
  const [billing, setBilling] = useState<BillingStatistics | null>(null);
  const [catalog, setCatalog] = useState<CatalogStatistics | null>(null);
  const [failures, setFailures] = useState<Partial<Record<Domain, string>>>({});
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    const results = await Promise.allSettled([
      getUserStatistics(),
      getBillingStatistics(),
      getCatalogStatistics(),
    ]);
    const nextFailures: Partial<Record<Domain, string>> = {};

    if (results[0].status === "fulfilled") setUsers(results[0].value);
    else { setUsers(null); nextFailures.users = message(results[0].reason); }
    if (results[1].status === "fulfilled") setBilling(results[1].value);
    else { setBilling(null); nextFailures.billing = message(results[1].reason); }
    if (results[2].status === "fulfilled") setCatalog(results[2].value);
    else { setCatalog(null); nextFailures.catalog = message(results[2].reason); }

    setFailures(nextFailures);
    setLoading(false);
  }

  useEffect(() => {
    void load();
  }, []);

  const generatedAt = useMemo(() => {
    const values = [users?.generatedAt, billing?.generatedAt, catalog?.generatedAt].filter(Boolean) as string[];
    return values.length ? values.sort().at(-1) : null;
  }, [billing, catalog, users]);
  const hasFailures = Object.keys(failures).length > 0;

  const metrics = [
    {
      icon: Users,
      label: "注册用户",
      value: value(users?.totalUsers),
      note: users ? `${formatInteger(users.activeUsers)} 个启用 · 本月新增 ${formatInteger(users.createdThisMonth)}` : "User Service 暂无数据",
    },
    {
      icon: WalletCards,
      label: "有效订阅",
      value: value(billing?.activeSubscriptions),
      note: billing ? `${formatInteger(billing.publishedPlans)} 个可用套餐 · ${formatInteger(billing.pendingOrders)} 个待支付订单` : "Billing Service 暂无数据",
    },
    {
      icon: Activity,
      label: "今日模型请求",
      value: value(billing?.modelRequestsToday),
      note: billing ? `${formatInteger(billing.completedModelRequestsToday)} 个已完成 · ${formatInteger(billing.pendingReconciliationToday)} 个待对账` : "Billing Service 暂无数据",
    },
    {
      icon: Boxes,
      label: "当前可用模型",
      value: value(catalog?.publicModels),
      note: catalog ? `${formatInteger(catalog.activeProviders)} 个活跃供应商 · ${formatInteger(catalog.draftModels)} 个草稿` : "Model Catalog 暂无数据",
    },
  ];

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-1 text-sm text-muted">管理控制台</p>
          <h1 className="text-2xl font-semibold tracking-tight">运营总览</h1>
          <p className="mt-2 text-sm text-muted">
            统计由各领域服务直接聚合 MySQL 最终事实，不使用前端抽样数据。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Chip color={loading ? "default" : hasFailures ? "warning" : "success"} variant="soft">
            {loading ? <Clock3 size={14} /> : hasFailures ? <ServerCog size={14} /> : <CheckCircle2 size={14} />}
            {loading ? "正在同步" : hasFailures ? "部分统计不可用" : "实时数据已同步"}
          </Chip>
          <Button isDisabled={loading} onPress={() => void load()} size="sm" variant="secondary">
            <RefreshCw size={15} /> 刷新
          </Button>
        </div>
      </header>

      {hasFailures && (
        <div className="rounded-xl bg-warning-soft px-4 py-3 text-sm text-warning" role="status">
          {Object.entries(failures).map(([domain, detail]) => `${domainLabel(domain as Domain)}：${detail}`).join("；")}
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {metrics.map(({ icon: Icon, label, value: metricValue, note }) => (
          <Card key={label} variant="default">
            <Card.Content className="gap-3">
              <span className="grid size-9 place-items-center rounded-xl bg-default text-muted"><Icon size={18} /></span>
              <div><p className="text-sm text-muted">{label}</p><strong className="mt-1 block text-2xl font-semibold tabular-nums">{loading && metricValue === "—" ? "…" : metricValue}</strong></div>
              <p className="text-xs text-muted">{note}</p>
            </Card.Content>
          </Card>
        ))}
      </section>

      <section className="grid items-start gap-6 xl:grid-cols-2">
        <Card variant="default">
          <Card.Header className="flex-row items-center justify-between">
            <div><Card.Title>本月订单与收入</Card.Title><Card.Description>按支付完成时间统计，收入不跨币种相加</Card.Description></div>
            <CircleDollarSign className="text-muted" size={19} />
          </Card.Header>
          <Card.Content className="gap-5 pt-2">
            <div className="grid gap-3 sm:grid-cols-3">
              <Stat label="已完成订单" value={value(billing?.fulfilledOrdersThisMonth)} />
              <Stat label="当前待支付" value={value(billing?.pendingOrders)} />
              <Stat label="有效订阅" value={value(billing?.activeSubscriptions)} />
            </div>
            <div className="divide-y divide-separator rounded-xl border border-border px-4">
              {!billing ? (
                <p className="py-6 text-center text-sm text-muted">暂无计费统计。</p>
              ) : billing.revenueThisMonth.length === 0 ? (
                <div className="flex items-center justify-between py-4 text-sm"><span className="text-muted">本月尚无已完成购买订单</span><strong>—</strong></div>
              ) : billing.revenueThisMonth.map((revenue) => (
                <div className="flex items-center justify-between gap-4 py-4" key={revenue.currency}>
                  <div><p className="text-sm font-medium">{revenue.currency}</p><p className="text-xs text-muted">{formatInteger(revenue.orderCount)} 个订单</p></div>
                  <strong className="text-lg font-semibold tabular-nums">{formatMoney(revenue.amountMinor, revenue.currency)}</strong>
                </div>
              ))}
            </div>
          </Card.Content>
        </Card>

        <Card variant="default">
          <Card.Header className="flex-row items-center justify-between">
            <div><Card.Title>今日模型调用</Card.Title><Card.Description>以 Billing 权威 Usage 记录为准</Card.Description></div>
            <Activity className="text-muted" size={19} />
          </Card.Header>
          <Card.Content className="gap-5 pt-2">
            <div className="grid gap-3 sm:grid-cols-2">
              <Stat label="请求总数" value={value(billing?.modelRequestsToday)} />
              <Stat label="结算完成" value={value(billing?.completedModelRequestsToday)} />
              <Stat label="待对账" value={value(billing?.pendingReconciliationToday)} tone={billing?.pendingReconciliationToday ? "warning" : "default"} />
              <Stat label="已消耗套餐额度" value={billing ? formatQuota(billing.billedQuotaToday) : "—"} />
            </div>
            <p className="text-xs leading-5 text-muted">
              当前没有独立、可靠的失败事实统计，因此不展示推算失败率；未知供应商结果统一进入待对账。
            </p>
          </Card.Content>
        </Card>
      </section>

      <section className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.1fr)_minmax(320px,.9fr)]">
        <Card variant="default">
          <Card.Header className="flex-row items-center justify-between">
            <div><Card.Title>平台资源</Card.Title><Card.Description>账号、会话、模型和版本的当前数量</Card.Description></div>
            <Database className="text-muted" size={19} />
          </Card.Header>
          <Card.Content className="grid gap-3 pt-2 sm:grid-cols-2 lg:grid-cols-3">
            <Stat label="启用用户" value={value(users?.activeUsers)} />
            <Stat label="停用用户" value={value(users?.disabledUsers)} />
            <Stat label="有效登录会话" value={value(users?.activeSessions)} />
            <Stat label="供应商总数" value={value(catalog?.totalProviders)} />
            <Stat label="模型定义" value={value(catalog?.totalModels)} />
            <Stat label="模型历史版本" value={value(catalog?.totalVersions)} />
          </Card.Content>
        </Card>

        <Card variant="default">
          <Card.Header><Card.Title>统计数据源</Card.Title><Card.Description>每个服务只统计自己拥有的数据</Card.Description></Card.Header>
          <Card.Content className="gap-0 pt-2">
            <SourceStatus label="User Service" detail="用户与有效会话" ready={Boolean(users)} failed={Boolean(failures.users)} />
            <SourceStatus label="Billing Service" detail="订单、订阅与 Usage" ready={Boolean(billing)} failed={Boolean(failures.billing)} />
            <SourceStatus label="Model Catalog" detail="供应商、模型与版本" ready={Boolean(catalog)} failed={Boolean(failures.catalog)} />
            <SourceStatus label="Model Gateway" detail="调用结果由 Billing Usage 汇总" ready={Boolean(billing)} failed={Boolean(failures.billing)} />
          </Card.Content>
          <Card.Footer className="text-xs text-muted">
            {generatedAt ? `${billing?.reportingZone ?? users?.reportingZone ?? "Asia/Shanghai"} · 更新于 ${formatDate(generatedAt)}` : "尚未取得统计时间"}
          </Card.Footer>
        </Card>
      </section>
    </div>
  );
}

function Stat({ label, value: statValue, tone = "default" }: { label: string; value: string; tone?: "default" | "warning" }) {
  return (
    <div className={`rounded-xl px-3 py-3 ${tone === "warning" ? "bg-warning-soft text-warning" : "bg-default/40"}`}>
      <p className={`text-xs ${tone === "warning" ? "text-warning" : "text-muted"}`}>{label}</p>
      <p className="mt-1 text-lg font-semibold tabular-nums">{statValue}</p>
    </div>
  );
}

function SourceStatus({ label, detail, ready, failed }: { label: string; detail: string; ready: boolean; failed: boolean }) {
  return (
    <div className="flex items-center gap-3 border-b border-separator py-3 last:border-0">
      <span className={`size-2 rounded-full ${failed ? "bg-warning" : ready ? "bg-success" : "bg-default-400"}`} />
      <div className="min-w-0 flex-1"><p className="text-sm font-medium">{label}</p><p className="text-xs text-muted">{detail}</p></div>
      <Chip color={failed ? "warning" : ready ? "success" : "default"} size="sm" variant="soft">
        {failed ? "不可用" : ready ? "已同步" : "等待中"}
      </Chip>
    </div>
  );
}

function value(input: number | undefined): string {
  return input === undefined ? "—" : formatInteger(input);
}

function formatInteger(input: number): string {
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 0 }).format(input);
}

function formatQuota(input: number): string {
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 6 }).format(input);
}

function formatMoney(minor: number, currency: string): string {
  return new Intl.NumberFormat("zh-CN", { style: "currency", currency, maximumFractionDigits: 2 }).format(minor / 100);
}

function formatDate(input: string): string {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(input));
}

function domainLabel(domain: Domain): string {
  return { users: "用户统计", billing: "计费统计", catalog: "模型统计" }[domain];
}

function message(reason: unknown): string {
  return reason instanceof ApiClientError ? reason.message : "无法连接服务";
}
