import { Button, Card, Chip, ProgressBar } from "@heroui/react";
import {
  Activity,
  ArrowUpRight,
  CircleGauge,
  Clock3,
  RefreshCw,
  Sparkles,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { ApiClientError } from "../../api/auth";
import {
  getBillingHistory,
  getBillingOverview,
  type BillingHistory,
  type BillingOverview,
  type BillingUsage,
} from "../../api/billing";

export function ConsoleOverviewPage() {
  const [overview, setOverview] = useState<BillingOverview | null>(null);
  const [history, setHistory] = useState<BillingHistory | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    const [overviewResult, historyResult] = await Promise.allSettled([getBillingOverview(), getBillingHistory()]);
    if (overviewResult.status === "fulfilled") {
      setOverview(overviewResult.value);
    }
    if (historyResult.status === "fulfilled") {
      setHistory(historyResult.value);
    }
    const failure = overviewResult.status === "rejected"
      ? overviewResult.reason
      : historyResult.status === "rejected" ? historyResult.reason : null;
    if (failure) {
      setError(message(failure));
    }
    setLoading(false);
  }

  useEffect(() => {
    void load();
  }, []);

  const cycleUsage = useMemo(
    () => history?.usage.filter((usage) => isInCurrentCycle(usage, overview)) ?? [],
    [history, overview],
  );
  const daily = useMemo(() => lastSevenDays(history?.usage ?? []), [history]);

  if (loading) return <PageMessage text="正在读取套餐与额度…" />;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-1 text-sm text-muted">当前计费周期</p>
          <h1 className="text-2xl font-semibold tracking-tight">套餐概览</h1>
          <p className="mt-2 text-sm text-muted">这里展示 Billing Service 的实时套餐、额度和模型用量。</p>
        </div>
        <div className="flex gap-2">
          <Button onPress={() => void load()} variant="secondary"><RefreshCw size={16} /> 刷新</Button>
          <Button onPress={() => window.location.assign("/console/plans")} variant="primary">
            查看套餐 <ArrowUpRight size={16} />
          </Button>
        </div>
      </header>

      {error && <div className="rounded-xl bg-danger-soft px-4 py-3 text-sm text-danger" role="alert">{error}</div>}

      {overview ? (
        overview.hasActiveSubscription && overview.plan && overview.subscription && overview.quota
          ? <ActiveSubscription overview={overview} requestCount={cycleUsage.length} />
          : <NoSubscription />
      ) : (
        <Card variant="default"><Card.Content className="py-10 text-center text-sm text-muted">当前无法确认套餐状态，请稍后刷新。</Card.Content></Card>
      )}

      <section className="grid gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(320px,.65fr)]">
        <DailyUsage data={daily} />
        <UsageSummary history={history} usage={cycleUsage} />
      </section>

      <RecentUsage usage={history?.usage.slice(0, 5) ?? []} />
    </div>
  );
}

function ActiveSubscription({ overview, requestCount }: { overview: BillingOverview; requestCount: number }) {
  const { plan, subscription, quota } = overview;
  if (!plan || !subscription || !quota) return null;
  const remainingPercent = quota.granted > 0 ? clamp((quota.remaining / quota.granted) * 100) : 0;

  return (
    <Card variant="default">
      <Card.Header className="flex-row items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <Card.Title>{plan.name}</Card.Title>
            <Chip color="success" size="sm" variant="soft">有效</Chip>
            <Chip size="sm" variant="tertiary">第 {quota.periodNo} 个额度周期</Chip>
          </div>
          <Card.Description className="mt-1">
            {formatMoney(plan.monthlyPriceMinor, plan.currency)} / 月 · 每周 {formatQuota(plan.weeklyQuota)} 额度
          </Card.Description>
        </div>
        <span className="text-right text-xs text-muted">订阅至<br /><strong className="text-sm font-medium text-foreground">{formatDate(subscription.endsAt)}</strong></span>
      </Card.Header>
      <Card.Content className="gap-6 pt-2">
        <div>
          <p className="text-sm text-muted">本周期剩余额度</p>
          <strong className="mt-1 block text-3xl font-semibold tracking-tight">{formatQuota(quota.remaining)}</strong>
        </div>

        <ProgressBar aria-label={`本周期额度剩余 ${remainingPercent.toFixed(1)}%`} color="accent" value={remainingPercent}>
          <ProgressBar.Output>{remainingPercent.toFixed(1)}%</ProgressBar.Output>
          <ProgressBar.Track><ProgressBar.Fill /></ProgressBar.Track>
        </ProgressBar>

        <div className="grid gap-4 border-t border-separator pt-4 sm:grid-cols-2 lg:grid-cols-5">
          <QuotaMeta label="已结算" value={formatQuota(quota.consumed)} />
          <QuotaMeta label="处理中预占" value={formatQuota(quota.reserved)} />
          <QuotaMeta label="周期总额度" value={formatQuota(quota.granted)} />
          <QuotaMeta label="近期记录请求" value={String(requestCount)} />
          <div>
            <p className="text-xs text-muted">下次刷新</p>
            <p className="mt-1 flex items-center gap-2 text-sm font-medium"><RefreshCw size={15} /> {relativeTime(quota.endsAt)}</p>
            <p className="mt-1 text-xs text-muted">{formatDateTime(quota.endsAt)}</p>
          </div>
        </div>
      </Card.Content>
    </Card>
  );
}

function NoSubscription() {
  return (
    <Card variant="default">
      <Card.Content className="items-center py-12 text-center">
        <CircleGauge className="text-muted" size={28} />
        <p className="mt-3 text-sm font-medium">当前没有生效中的套餐</p>
        <p className="mt-1 max-w-md text-xs leading-5 text-muted">可以浏览已发布套餐。自助购买尚未开放时，管理员也可以在管理端为账号发放测试订阅。</p>
        <Button className="mt-4" onPress={() => window.location.assign("/console/plans")} variant="primary">查看套餐</Button>
      </Card.Content>
    </Card>
  );
}

function DailyUsage({ data }: { data: DailyUsagePoint[] }) {
  const max = Math.max(...data.map((point) => point.tokens), 0);
  const total = data.reduce((sum, point) => sum + point.tokens, 0);

  return (
    <Card variant="default">
      <Card.Header className="flex-row items-start justify-between gap-4">
        <div><Card.Title>每日 Token</Card.Title><Card.Description>最近 100 条记录中的近 7 天用量</Card.Description></div>
        <span className="text-xs text-muted">共 {formatTokens(total)}</span>
      </Card.Header>
      <Card.Content>
        {total === 0 ? (
          <div className="grid h-48 place-items-center border-t border-separator text-sm text-muted">最近 7 天暂无模型调用</div>
        ) : (
          <div className="flex h-48 items-end gap-3 border-t border-separator pt-5">
            {data.map((point) => (
              <div className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-2" key={point.key} title={`${point.label}：${formatTokens(point.tokens)}`}>
                <span className="w-full max-w-8 rounded-t-md bg-accent" style={{ height: `${Math.max((point.tokens / max) * 100, point.tokens > 0 ? 4 : 0)}%` }} />
                <small className="text-xs text-muted">{point.weekday}</small>
              </div>
            ))}
          </div>
        )}
      </Card.Content>
    </Card>
  );
}

function UsageSummary({ history, usage }: { history: BillingHistory | null; usage: BillingUsage[] }) {
  const totalTokens = usage.reduce((sum, item) => sum + tokenTotal(item), 0);
  const billedQuota = usage.reduce((sum, item) => sum + item.billedQuota, 0);
  const pendingCount = usage.filter((item) => item.status === "PENDING_RECONCILIATION").length;
  const latestModel = history?.usage[0]?.modelCode;

  return (
    <Card variant="default">
      <Card.Header><Card.Title>近期用量</Card.Title><Card.Description>最近 100 条记录中的当前额度周期</Card.Description></Card.Header>
      <Card.Content className="gap-4 pt-2">
        <SummaryRow icon={Activity} label="模型请求" value={String(usage.length)} />
        <SummaryRow icon={Sparkles} label="Token 合计" value={formatTokens(totalTokens)} />
        <SummaryRow icon={CircleGauge} label="结算额度" value={formatQuota(billedQuota)} />
        <SummaryRow icon={Clock3} label="待对账" value={String(pendingCount)} warning={pendingCount > 0} />
        <div className="border-t border-separator pt-3"><p className="text-xs text-muted">最近调用模型</p><p className="mt-1 truncate font-mono text-xs">{latestModel ?? "暂无"}</p></div>
      </Card.Content>
    </Card>
  );
}

function RecentUsage({ usage }: { usage: BillingUsage[] }) {
  return (
    <Card variant="default">
      <Card.Header className="flex-row items-center justify-between">
        <div><Card.Title>最近模型调用</Card.Title><Card.Description>服务端结算后的最近记录</Card.Description></div>
        <Button onPress={() => window.location.assign("/console/usage")} size="sm" variant="tertiary">查看全部 <ArrowUpRight size={15} /></Button>
      </Card.Header>
      <Card.Content className="pt-1">
        {usage.length === 0 ? <p className="py-6 text-center text-sm text-muted">暂无用量记录。</p> : (
          <div className="divide-y divide-separator">
            {usage.map((item) => (
              <div className="grid gap-2 py-3 text-sm sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center" key={item.usageId}>
                <div className="min-w-0"><p className="truncate font-medium">{item.modelCode}</p><p className="text-xs text-muted">{formatDateTime(item.occurredAt)}</p></div>
                <span className="text-xs text-muted">{formatTokens(tokenTotal(item))} Token</span>
                <UsageStatus status={item.status} />
              </div>
            ))}
          </div>
        )}
      </Card.Content>
    </Card>
  );
}

function SummaryRow({ icon: Icon, label, value, warning = false }: { icon: typeof Activity; label: string; value: string; warning?: boolean }) {
  return <div className="flex items-center gap-3"><span className="grid size-9 place-items-center rounded-xl bg-default text-muted"><Icon size={17} /></span><div className="min-w-0 flex-1"><p className="text-xs text-muted">{label}</p><p className={warning ? "text-sm font-medium text-warning" : "text-sm font-medium"}>{value}</p></div></div>;
}

function UsageStatus({ status }: { status: BillingUsage["status"] }) {
  const complete = status === "COMPLETED";
  const pending = status === "PENDING_RECONCILIATION" || status === "PROCESSING";
  return <Chip color={complete ? "success" : pending ? "warning" : "danger"} size="sm" variant="soft">{complete ? "已结算" : pending ? "待处理" : "失败"}</Chip>;
}

function QuotaMeta({ label, value }: { label: string; value: string }) {
  return <div><p className="text-xs text-muted">{label}</p><p className="mt-1 text-sm font-medium">{value}</p></div>;
}

function PageMessage({ text }: { text: string }) {
  return <div className="grid min-h-[50vh] place-items-center text-sm text-muted">{text}</div>;
}

interface DailyUsagePoint {
  key: string;
  label: string;
  weekday: string;
  tokens: number;
}

function lastSevenDays(usage: BillingUsage[]): DailyUsagePoint[] {
  const formatter = new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric" });
  const weekday = new Intl.DateTimeFormat("zh-CN", { weekday: "short" });
  const totals = new Map<string, number>();
  for (const item of usage) {
    const key = localDateKey(new Date(item.occurredAt));
    totals.set(key, (totals.get(key) ?? 0) + tokenTotal(item));
  }
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setHours(12, 0, 0, 0);
    date.setDate(date.getDate() - (6 - index));
    const key = localDateKey(date);
    return { key, label: formatter.format(date), weekday: weekday.format(date).replace("周", ""), tokens: totals.get(key) ?? 0 };
  });
}

function isInCurrentCycle(usage: BillingUsage, overview: BillingOverview | null): boolean {
  if (!overview?.quota) return false;
  const occurred = new Date(usage.occurredAt).getTime();
  return occurred >= new Date(overview.quota.startsAt).getTime() && occurred < new Date(overview.quota.endsAt).getTime();
}

function tokenTotal(usage: BillingUsage): number {
  return usage.inputTokens + usage.outputTokens + usage.reasoningTokens + usage.cacheReadTokens + usage.cacheWriteTokens;
}

function localDateKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function clamp(value: number): number {
  return Math.max(0, Math.min(100, value));
}

function relativeTime(value: string): string {
  const milliseconds = new Date(value).getTime() - Date.now();
  if (milliseconds <= 0) return "即将刷新";
  const hours = Math.ceil(milliseconds / 3_600_000);
  if (hours < 24) return `${hours} 小时后`;
  return `${Math.ceil(hours / 24)} 天后`;
}

function formatMoney(minor: number, currency: string): string {
  return new Intl.NumberFormat("zh-CN", { style: "currency", currency }).format(minor / 100);
}

function formatQuota(value: number): string {
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 6 }).format(value);
}

function formatTokens(value: number): string {
  return new Intl.NumberFormat("zh-CN", { notation: value >= 10_000 ? "compact" : "standard", maximumFractionDigits: 1 }).format(value);
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium" }).format(new Date(value));
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function message(reason: unknown): string {
  return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试";
}
