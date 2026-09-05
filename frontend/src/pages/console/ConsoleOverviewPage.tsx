import { Button, Card, Chip, ProgressBar } from "@heroui/react";
import {
  Activity,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  CircleGauge,
  Clock3,
  RefreshCw,
  Sparkles,
} from "lucide-react";
import { useEffect, useState } from "react";

import { ApiClientError } from "../../api/auth";
import {
  getBillingHistory,
  getBillingOverview,
  getBillingUsageChart,
  type BillingHistory,
  type BillingOverview,
  type BillingUsage,
  type BillingUsageChart,
  type DailyBillingUsage,
  type UsageChartRange,
} from "../../api/billing";

export function ConsoleOverviewPage() {
  const [overview, setOverview] = useState<BillingOverview | null>(null);
  const [history, setHistory] = useState<BillingHistory | null>(null);
  const [chart, setChart] = useState<BillingUsageChart | null>(null);
  const [chartRange, setChartRange] = useState<UsageChartRange>("WEEK");
  const [chartAnchor, setChartAnchor] = useState(todayInReportingZone());
  const [loading, setLoading] = useState(true);
  const [chartLoading, setChartLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [chartError, setChartError] = useState<string | null>(null);

  async function loadMain() {
    setLoading(true);
    setError(null);
    const [overviewResult, historyResult] = await Promise.allSettled([
      getBillingOverview(),
      getBillingHistory("CURRENT_PERIOD"),
    ]);
    if (overviewResult.status === "fulfilled") setOverview(overviewResult.value);
    if (historyResult.status === "fulfilled") setHistory(historyResult.value);
    const failure = overviewResult.status === "rejected"
      ? overviewResult.reason
      : historyResult.status === "rejected" ? historyResult.reason : null;
    if (failure) setError(message(failure));
    setLoading(false);
  }

  async function loadChart(range: UsageChartRange, anchor: string) {
    setChartLoading(true);
    setChartError(null);
    try {
      setChart(await getBillingUsageChart(range, anchor));
    } catch (reason) {
      setChartError(message(reason));
    } finally {
      setChartLoading(false);
    }
  }

  useEffect(() => {
    void loadMain();
  }, []);

  useEffect(() => {
    void loadChart(chartRange, chartAnchor);
  }, [chartRange, chartAnchor]);

  function changeChartRange(range: UsageChartRange) {
    setChartRange(range);
    setChartAnchor(todayInReportingZone());
  }

  function moveChart(direction: -1 | 1) {
    const base = chart?.startsOn ?? chartAnchor;
    setChartAnchor(shiftPeriod(base, chartRange, direction));
  }

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
          <Button isDisabled={chartLoading} onPress={() => { void loadMain(); void loadChart(chartRange, chartAnchor); }} variant="secondary"><RefreshCw size={16} /> 刷新</Button>
          <Button onPress={() => window.location.assign("/console/plans")} variant="primary">
            查看套餐 <ArrowUpRight size={16} />
          </Button>
        </div>
      </header>

      {error && <div className="rounded-xl bg-danger-soft px-4 py-3 text-sm text-danger" role="alert">{error}</div>}

      {overview ? (
        overview.hasActiveSubscription && overview.plan && overview.subscription && overview.quota
          ? <ActiveSubscription overview={overview} requestCount={history?.usageSummary.requestCount ?? 0} />
          : <NoSubscription />
      ) : (
        <Card variant="default"><Card.Content className="py-10 text-center text-sm text-muted">当前无法确认套餐状态，请稍后刷新。</Card.Content></Card>
      )}

      {!!overview?.scheduledSubscriptions?.length && (
        <Card variant="default">
          <Card.Header><div><Card.Title>待生效的套餐</Card.Title><Card.Description>权益已到账，将按以下顺序自动生效；购买不同套餐也会接续现有排期。</Card.Description></div></Card.Header>
          <Card.Content className="gap-0 pt-1">
            {overview.scheduledSubscriptions.map(({ subscription, plan }) => (
              <div className="grid items-start gap-3 border-b border-separator py-4 last:border-0 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]" key={subscription.subscriptionId}>
                <div className="min-w-0"><p className="flex flex-wrap items-center gap-2 font-medium">{plan.name}<Chip size="sm" color="warning" variant="soft">待生效</Chip></p><p className="mt-1 text-xs text-muted">每周 {formatQuota(plan.weeklyQuota)} 额度</p></div>
                <div className="min-w-0 text-sm"><p>{formatDateTime(subscription.startsAt)} 至 {formatDateTime(subscription.endsAt)}</p>{subscription.source === "PURCHASE" && subscription.sourceReference && <a className="mt-1 block break-all text-xs text-accent underline" href={`/console/orders/${encodeURIComponent(subscription.sourceReference)}`}>订单 {subscription.sourceReference}</a>}</div>
              </div>
            ))}
          </Card.Content>
        </Card>
      )}

      <section className="grid gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(320px,.65fr)]">
        <DailyUsage
          chart={chart}
          error={chartError}
          loading={chartLoading}
          onMove={moveChart}
          onRangeChange={changeChartRange}
          range={chartRange}
        />
        <UsageSummary history={history} />
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
          <QuotaMeta label="本周期请求" value={formatNumber(requestCount)} />
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

function DailyUsage({
  chart,
  error,
  loading,
  onMove,
  onRangeChange,
  range,
}: {
  chart: BillingUsageChart | null;
  error: string | null;
  loading: boolean;
  onMove: (direction: -1 | 1) => void;
  onRangeChange: (range: UsageChartRange) => void;
  range: UsageChartRange;
}) {
  const points = chart?.points ?? [];
  const max = Math.max(...points.map(dailyTokens), 0);
  const total = chart ? usageSummaryTokens(chart.summary) : 0;
  const canMoveNext = Boolean(chart && chart.endsOnExclusive <= todayInReportingZone());

  return (
    <Card variant="default">
      <Card.Header className="flex-col items-stretch gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <Card.Title>每日 Token</Card.Title>
          <Card.Description>{chart ? `${formatChartRange(chart)} · ${chart.reportingZone}` : "按自然周或自然月统计"}</Card.Description>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex rounded-xl bg-default p-1" role="group" aria-label="图表周期">
            <Button onPress={() => onRangeChange("WEEK")} size="sm" variant={range === "WEEK" ? "primary" : "ghost"}>周</Button>
            <Button onPress={() => onRangeChange("MONTH")} size="sm" variant={range === "MONTH" ? "primary" : "ghost"}>月</Button>
          </div>
          <Button aria-label={range === "WEEK" ? "上一周" : "上一月"} isDisabled={loading} isIconOnly onPress={() => onMove(-1)} size="sm" variant="tertiary"><ChevronLeft size={16} /></Button>
          <Button aria-label={range === "WEEK" ? "下一周" : "下一月"} isDisabled={loading || !canMoveNext} isIconOnly onPress={() => onMove(1)} size="sm" variant="tertiary"><ChevronRight size={16} /></Button>
        </div>
      </Card.Header>
      <Card.Content>
        <div className="flex items-center justify-between border-t border-separator pt-4 text-xs text-muted">
          <span>{range === "WEEK" ? "自然周" : "自然月"}汇总</span>
          <span>共 {formatTokens(total)} Token</span>
        </div>
        {loading ? (
          <div className="grid h-48 place-items-center text-sm text-muted">正在读取 Token 用量…</div>
        ) : error ? (
          <div className="grid h-48 place-items-center text-sm text-danger">{error}</div>
        ) : total === 0 ? (
          <div className="grid h-48 place-items-center text-sm text-muted">所选时间段暂无模型调用</div>
        ) : (
          <div
            className={range === "MONTH" ? "grid h-48 items-end gap-1 pt-5" : "grid h-48 items-end gap-3 pt-5"}
            style={{ gridTemplateColumns: `repeat(${points.length}, minmax(0, 1fr))` }}
          >
            {points.map((point, index) => {
              const tokens = dailyTokens(point);
              return (
                <div className="flex h-full min-w-0 flex-col items-center justify-end gap-2" key={point.date} title={`${formatLocalDate(point.date)}：${formatNumber(tokens)} Token`}>
                  <span className="w-full max-w-8 rounded-t-md bg-accent" style={{ height: `${Math.max((tokens / max) * 100, tokens > 0 ? 4 : 0)}%` }} />
                  <small className="h-4 whitespace-nowrap text-[10px] text-muted">{axisLabel(point.date, index, points.length, range)}</small>
                </div>
              );
            })}
          </div>
        )}
      </Card.Content>
    </Card>
  );
}

function UsageSummary({ history }: { history: BillingHistory | null }) {
  const summary = history?.usageSummary;
  const latestModel = history?.usage[0]?.modelCode;

  return (
    <Card variant="default">
      <Card.Header><Card.Title>本周期用量</Card.Title><Card.Description>基于当前额度周期的完整服务端统计</Card.Description></Card.Header>
      <Card.Content className="gap-4 pt-2">
        <SummaryRow icon={Activity} label="模型请求" value={formatNumber(summary?.requestCount ?? 0)} />
        <SummaryRow icon={Sparkles} label="Token 合计" value={formatTokens(summary ? usageSummaryTokens(summary) : 0)} />
        <SummaryRow icon={CircleGauge} label="结算额度" value={formatQuota(summary?.billedQuota ?? 0)} />
        <SummaryRow icon={Clock3} label="待对账" value={formatNumber(summary?.pendingCount ?? 0)} warning={(summary?.pendingCount ?? 0) > 0} />
        <div className="border-t border-separator pt-3"><p className="text-xs text-muted">最近调用模型</p><p className="mt-1 truncate font-mono text-xs">{latestModel ?? "暂无"}</p></div>
      </Card.Content>
    </Card>
  );
}

function RecentUsage({ usage }: { usage: BillingUsage[] }) {
  return (
    <Card variant="default">
      <Card.Header className="flex-row items-center justify-between">
        <div><Card.Title>最近模型调用</Card.Title><Card.Description>当前额度周期内最近的服务端结算记录</Card.Description></div>
        <Button onPress={() => window.location.assign("/console/usage")} size="sm" variant="tertiary">查看明细 <ArrowUpRight size={15} /></Button>
      </Card.Header>
      <Card.Content className="pt-1">
        {usage.length === 0 ? <p className="py-6 text-center text-sm text-muted">当前额度周期暂无用量记录。</p> : (
          <div className="divide-y divide-separator">
            {usage.map((item) => (
              <div className="grid gap-2 py-3 text-sm sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center" key={item.usageId}>
                <div className="min-w-0"><p className="truncate font-medium">{item.modelCode}</p><p className="text-xs text-muted">{formatDateTime(item.occurredAt)}</p></div>
                <span className="text-xs text-muted">{formatTokens(usageTokens(item))} Token</span>
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

function usageSummaryTokens(summary: BillingHistory["usageSummary"]): number {
  return summary.inputTokens + summary.outputTokens + summary.reasoningTokens + summary.cacheReadTokens + summary.cacheWriteTokens;
}

function dailyTokens(usage: DailyBillingUsage): number {
  return usage.inputTokens + usage.outputTokens + usage.reasoningTokens + usage.cacheReadTokens + usage.cacheWriteTokens;
}

function usageTokens(usage: BillingUsage): number {
  return usage.inputTokens + usage.outputTokens + usage.reasoningTokens + usage.cacheReadTokens + usage.cacheWriteTokens;
}

function axisLabel(date: string, index: number, length: number, range: UsageChartRange): string {
  const value = parseLocalDate(date);
  if (range === "WEEK") return new Intl.DateTimeFormat("zh-CN", { weekday: "short", timeZone: "UTC" }).format(value).replace("周", "");
  if (index !== 0 && index !== length - 1 && index % 5 !== 0) return "";
  return `${value.getUTCMonth() + 1}/${value.getUTCDate()}`;
}

function formatChartRange(chart: BillingUsageChart): string {
  const end = parseLocalDate(chart.endsOnExclusive);
  end.setUTCDate(end.getUTCDate() - 1);
  return `${formatLocalDate(chart.startsOn)} – ${new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric", timeZone: "UTC" }).format(end)}`;
}

function formatLocalDate(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric", timeZone: "UTC" }).format(parseLocalDate(value));
}

function parseLocalDate(value: string): Date {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}

function shiftPeriod(value: string, range: UsageChartRange, direction: -1 | 1): string {
  const date = parseLocalDate(value);
  if (range === "MONTH") {
    date.setUTCDate(1);
    date.setUTCMonth(date.getUTCMonth() + direction);
  } else {
    date.setUTCDate(date.getUTCDate() + direction * 7);
  }
  return date.toISOString().slice(0, 10);
}

function todayInReportingZone(): string {
  const parts = new Intl.DateTimeFormat("en", {
    day: "2-digit",
    month: "2-digit",
    timeZone: "Asia/Shanghai",
    year: "numeric",
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
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

function formatNumber(value: number): string {
  return new Intl.NumberFormat("zh-CN").format(value);
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
