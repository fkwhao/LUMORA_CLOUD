import { Button, Card, Chip } from "@heroui/react";
import { ArrowLeft, BookOpen, CircleGauge, RefreshCw, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";

import { ApiClientError } from "../../api/auth";
import {
  getBillingHistory,
  type BillingHistory,
  type BillingHistoryScope,
  type BillingLedgerEntry,
  type BillingUsage,
} from "../../api/billing";

export function BillingHistoryPage({ mode }: { mode: "usage" | "ledger" }) {
  const [scope, setScope] = useState<BillingHistoryScope>("CURRENT_PERIOD");
  const [history, setHistory] = useState<BillingHistory | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load(targetScope: BillingHistoryScope) {
    setLoading(true);
    setError(null);
    try {
      setHistory(await getBillingHistory(targetScope));
    } catch (reason) {
      setError(message(reason));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(scope);
  }, [scope]);

  const isUsage = mode === "usage";

  return (
    <div className="space-y-6">
      <Button onPress={() => window.location.assign("/console")} size="sm" variant="ghost">
        <ArrowLeft size={15} /> 返回概览
      </Button>

      <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="mb-1 text-sm text-muted">套餐与额度</p>
          <h1 className="text-2xl font-semibold tracking-tight">{isUsage ? "模型用量明细" : "额度流水"}</h1>
          <p className="mt-2 text-sm text-muted">
            {isUsage ? "统计使用服务端完整数据，明细仅展示所选范围内最近 100 条。" : "统计使用完整额度数据，流水仅展示所选范围内最近 100 条不可变记录。"}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ScopeSwitch loading={loading} onChange={setScope} scope={scope} />
          <Button isDisabled={loading} onPress={() => void load(scope)} variant="secondary">
            <RefreshCw size={16} /> 刷新
          </Button>
        </div>
      </header>

      {error && <div className="rounded-xl bg-danger-soft px-4 py-3 text-sm text-danger" role="alert">{error}</div>}

      {loading ? (
        <div className="grid min-h-[45vh] place-items-center text-sm text-muted">正在读取记录…</div>
      ) : isUsage ? (
        <UsageList history={history} />
      ) : (
        <LedgerList history={history} />
      )}
    </div>
  );
}

function ScopeSwitch({
  loading,
  onChange,
  scope,
}: {
  loading: boolean;
  onChange: (scope: BillingHistoryScope) => void;
  scope: BillingHistoryScope;
}) {
  return (
    <div aria-label="统计范围" className="flex rounded-xl bg-default p-1" role="group">
      <Button
        isDisabled={loading}
        onPress={() => onChange("CURRENT_PERIOD")}
        size="sm"
        variant={scope === "CURRENT_PERIOD" ? "primary" : "ghost"}
      >
        当前额度周期
      </Button>
      <Button
        isDisabled={loading}
        onPress={() => onChange("CURRENT_MONTH")}
        size="sm"
        variant={scope === "CURRENT_MONTH" ? "primary" : "ghost"}
      >
        本月
      </Button>
    </div>
  );
}

function UsageList({ history }: { history: BillingHistory | null }) {
  const usage = history?.usage ?? [];
  const summary = history?.usageSummary;
  const totalTokens = summary ? usageSummaryTokens(summary) : 0;

  return (
    <>
      <RangeNotice history={history} />
      <section className="grid gap-4 sm:grid-cols-3">
        <Summary icon={Sparkles} label="模型请求" value={formatNumber(summary?.requestCount ?? 0)} />
        <Summary icon={CircleGauge} label="Token 合计" value={formatNumber(totalTokens)} />
        <Summary icon={BookOpen} label="结算额度" value={formatQuota(summary?.billedQuota ?? 0)} />
      </section>
      <Card variant="default">
        <Card.Header>
          <div>
            <Card.Title>最近模型调用</Card.Title>
            <Card.Description>{detailDescription(summary?.requestCount ?? 0, history?.detailLimit ?? 100)}</Card.Description>
          </div>
        </Card.Header>
        <Card.Content className="pt-1">
          {usage.length === 0 ? <Empty text="所选范围内暂无模型用量记录。" /> : (
            <div className="divide-y divide-separator">
              {usage.map((item) => (
                <div className="grid gap-3 py-4 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,.8fr)_auto] lg:items-center" key={item.usageId}>
                  <div className="min-w-0"><p className="truncate text-sm font-medium">{item.modelCode}</p><p className="mt-1 truncate font-mono text-xs text-muted" title={item.requestId}>{item.requestId}</p></div>
                  <div className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs text-muted"><span>输入 {formatNumber(item.inputTokens)}</span><span>输出 {formatNumber(item.outputTokens)}</span><span>推理 {formatNumber(item.reasoningTokens)}</span><span>缓存读 {formatNumber(item.cacheReadTokens)}</span><span>缓存创建 {formatNumber(item.cacheWriteTokens)}</span></div>
                  <div><p className="text-sm">{formatQuota(item.billedQuota)} 额度</p><p className="text-xs text-muted">{formatDateTime(item.occurredAt)}</p></div>
                  <UsageStatus status={item.status} />
                </div>
              ))}
            </div>
          )}
        </Card.Content>
      </Card>
    </>
  );
}

function LedgerList({ history }: { history: BillingHistory | null }) {
  const ledger = history?.ledger ?? [];
  const summary = history?.quotaSummary;

  return (
    <>
      <RangeNotice history={history} />
      <section className="grid gap-4 sm:grid-cols-3">
        <Summary icon={Sparkles} label="额度发放变化" value={signed(summary?.grantedDelta ?? 0)} />
        <Summary icon={CircleGauge} label="预占变化" value={signed(summary?.reservedDelta ?? 0)} />
        <Summary icon={BookOpen} label="已用变化" value={signed(summary?.consumedDelta ?? 0)} />
      </section>
      <Card variant="default">
        <Card.Header>
          <div>
            <Card.Title>额度流水</Card.Title>
            <Card.Description>{detailDescription(summary?.entryCount ?? 0, history?.detailLimit ?? 100, "条不可变记录")}</Card.Description>
          </div>
        </Card.Header>
        <Card.Content className="pt-1">
          {ledger.length === 0 ? <Empty text="所选范围内暂无额度流水。" /> : (
            <div className="divide-y divide-separator">
              {ledger.map((entry) => (
                <div className="grid gap-3 py-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)_minmax(0,.9fr)_auto] lg:items-center" key={entry.id}>
                  <div><div className="flex items-center gap-2"><LedgerType type={entry.entryType} /><span className="text-sm font-medium">{entry.description || ledgerLabel(entry.entryType)}</span></div><p className="mt-1 text-xs text-muted">{formatDateTime(entry.createdAt)}</p></div>
                  <div className="min-w-0"><p className="text-xs text-muted">{entry.referenceType}</p><p className="truncate font-mono text-xs" title={entry.referenceId}>{entry.referenceId}</p></div>
                  <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs"><Delta label="发放" value={entry.grantedDelta} /><Delta label="预占" value={entry.reservedDelta} /><Delta label="已用" value={entry.consumedDelta} /></div>
                  <span className="font-mono text-xs text-muted">{entry.id.slice(0, 8)}</span>
                </div>
              ))}
            </div>
          )}
        </Card.Content>
      </Card>
    </>
  );
}

function RangeNotice({ history }: { history: BillingHistory | null }) {
  return (
    <div className="flex flex-wrap items-center gap-2 text-sm text-muted">
      <Chip size="sm" variant="soft">{history?.scope === "CURRENT_MONTH" ? "本月" : "当前额度周期"}</Chip>
      <span>{formatRange(history?.startsAt, history?.endsAt)}</span>
      <span>· 时区 {history?.reportingZone ?? "Asia/Shanghai"}</span>
    </div>
  );
}

function Summary({ icon: Icon, label, value }: { icon: typeof Sparkles; label: string; value: string }) {
  return <Card variant="default"><Card.Content className="flex-row items-center gap-3"><span className="grid size-9 place-items-center rounded-xl bg-default text-muted"><Icon size={18} /></span><div><p className="text-xs text-muted">{label}</p><p className="text-xl font-semibold tabular-nums">{value}</p></div></Card.Content></Card>;
}

function UsageStatus({ status }: { status: BillingUsage["status"] }) {
  const complete = status === "COMPLETED";
  const pending = status === "PENDING_RECONCILIATION" || status === "PROCESSING";
  return <Chip color={complete ? "success" : pending ? "warning" : "danger"} size="sm" variant="soft">{complete ? "已结算" : pending ? "待处理" : "失败"}</Chip>;
}

function LedgerType({ type }: { type: BillingLedgerEntry["entryType"] }) {
  const labels = { GRANT: "发放", RESERVE: "预占", SETTLE: "结算", RELEASE: "释放", ADJUSTMENT: "调整" } as const;
  const colors = { GRANT: "success", RESERVE: "warning", SETTLE: "accent", RELEASE: "default", ADJUSTMENT: "default" } as const;
  return <Chip color={colors[type]} size="sm" variant="soft">{labels[type]}</Chip>;
}

function Delta({ label, value }: { label: string; value: number }) {
  return <span className={value > 0 ? "text-success" : value < 0 ? "text-danger" : "text-muted"}>{label} {signed(value)}</span>;
}

function Empty({ text }: { text: string }) {
  return <p className="py-10 text-center text-sm text-muted">{text}</p>;
}

function usageSummaryTokens(summary: BillingHistory["usageSummary"]): number {
  return summary.inputTokens + summary.outputTokens + summary.reasoningTokens + summary.cacheReadTokens + summary.cacheWriteTokens;
}

function ledgerLabel(type: BillingLedgerEntry["entryType"]): string {
  return { GRANT: "周期额度发放", RESERVE: "模型请求预占", SETTLE: "模型请求结算", RELEASE: "释放预占额度", ADJUSTMENT: "额度调整" }[type];
}

function detailDescription(total: number, limit: number, noun = "条记录"): string {
  return total > limit ? `本范围共 ${formatNumber(total)} ${noun}，显示最近 ${limit} 条` : `本范围共 ${formatNumber(total)} ${noun}`;
}

function formatRange(startsAt?: string, endsAt?: string): string {
  if (!startsAt || !endsAt) return "当前没有生效中的额度周期";
  const formatter = new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "Asia/Shanghai" });
  return `${formatter.format(new Date(startsAt))} 至 ${formatter.format(new Date(endsAt))}`;
}

function signed(value: number): string {
  return `${value > 0 ? "+" : ""}${formatQuota(value)}`;
}

function formatQuota(value: number): string {
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 6 }).format(value);
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat("zh-CN").format(value);
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function message(reason: unknown): string {
  return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试";
}
