import { Button, Card, Chip } from "@heroui/react";
import { ArrowLeft, BookOpen, CircleGauge, RefreshCw, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";

import { ApiClientError } from "../../api/auth";
import {
  getBillingHistory,
  type BillingHistory,
  type BillingLedgerEntry,
  type BillingUsage,
} from "../../api/billing";

export function BillingHistoryPage({ mode }: { mode: "usage" | "ledger" }) {
  const [history, setHistory] = useState<BillingHistory | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setHistory(await getBillingHistory());
    } catch (reason) {
      setError(message(reason));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  const isUsage = mode === "usage";

  return (
    <div className="space-y-6">
      <Button onPress={() => window.location.assign("/console")} size="sm" variant="ghost">
        <ArrowLeft size={15} /> 返回概览
      </Button>

      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-1 text-sm text-muted">套餐与额度</p>
          <h1 className="text-2xl font-semibold tracking-tight">{isUsage ? "模型用量明细" : "额度流水"}</h1>
          <p className="mt-2 text-sm text-muted">
            {isUsage ? "展示服务端权威 Usage 和最终结算额度。" : "记录额度发放、预占、结算和释放的不可变变更。"}
          </p>
        </div>
        <Button isDisabled={loading} onPress={() => void load()} variant="secondary"><RefreshCw size={16} /> 刷新</Button>
      </header>

      {error && <div className="rounded-xl bg-danger-soft px-4 py-3 text-sm text-danger" role="alert">{error}</div>}

      {loading ? (
        <div className="grid min-h-[45vh] place-items-center text-sm text-muted">正在读取记录…</div>
      ) : isUsage ? (
        <UsageList usage={history?.usage ?? []} />
      ) : (
        <LedgerList ledger={history?.ledger ?? []} />
      )}
    </div>
  );
}

function UsageList({ usage }: { usage: BillingUsage[] }) {
  const totalTokens = usage.reduce((sum, item) => sum + tokenTotal(item), 0);
  const totalQuota = usage.reduce((sum, item) => sum + item.billedQuota, 0);

  return (
    <>
      <section className="grid gap-4 sm:grid-cols-3">
        <Summary icon={Sparkles} label="记录数" value={String(usage.length)} />
        <Summary icon={CircleGauge} label="Token 合计" value={formatNumber(totalTokens)} />
        <Summary icon={BookOpen} label="结算额度" value={formatQuota(totalQuota)} />
      </section>
      <Card variant="default">
        <Card.Header><Card.Title>最近模型调用</Card.Title><Card.Description>最多显示最近 100 条记录</Card.Description></Card.Header>
        <Card.Content className="pt-1">
          {usage.length === 0 ? <Empty text="暂无模型用量记录。" /> : (
            <div className="divide-y divide-separator">
              {usage.map((item) => (
                <div className="grid gap-3 py-4 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,.8fr)_auto] lg:items-center" key={item.usageId}>
                  <div className="min-w-0"><p className="truncate text-sm font-medium">{item.modelCode}</p><p className="mt-1 truncate font-mono text-xs text-muted" title={item.requestId}>{item.requestId}</p></div>
                  <div className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs text-muted"><span>输入 {formatNumber(item.inputTokens)}</span><span>输出 {formatNumber(item.outputTokens)}</span><span>推理 {formatNumber(item.reasoningTokens)}</span><span>缓存 {formatNumber(item.cacheReadTokens + item.cacheWriteTokens)}</span></div>
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

function LedgerList({ ledger }: { ledger: BillingLedgerEntry[] }) {
  const granted = ledger.reduce((sum, item) => sum + item.grantedDelta, 0);
  const consumed = ledger.reduce((sum, item) => sum + item.consumedDelta, 0);
  const reserved = ledger.reduce((sum, item) => sum + item.reservedDelta, 0);

  return (
    <>
      <section className="grid gap-4 sm:grid-cols-3">
        <Summary icon={Sparkles} label="额度发放变化" value={signed(granted)} />
        <Summary icon={CircleGauge} label="预占变化" value={signed(reserved)} />
        <Summary icon={BookOpen} label="已用变化" value={signed(consumed)} />
      </section>
      <Card variant="default">
        <Card.Header><Card.Title>额度流水</Card.Title><Card.Description>最多显示最近 100 条不可变记录</Card.Description></Card.Header>
        <Card.Content className="pt-1">
          {ledger.length === 0 ? <Empty text="暂无额度流水。" /> : (
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

function Summary({ icon: Icon, label, value }: { icon: typeof Sparkles; label: string; value: string }) {
  return <Card variant="default"><Card.Content className="flex-row items-center gap-3"><span className="grid size-9 place-items-center rounded-xl bg-default text-muted"><Icon size={18} /></span><div><p className="text-xs text-muted">{label}</p><p className="text-xl font-semibold">{value}</p></div></Card.Content></Card>;
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

function tokenTotal(usage: BillingUsage): number {
  return usage.inputTokens + usage.outputTokens + usage.reasoningTokens + usage.cacheReadTokens + usage.cacheWriteTokens;
}

function ledgerLabel(type: BillingLedgerEntry["entryType"]): string {
  return { GRANT: "周期额度发放", RESERVE: "模型请求预占", SETTLE: "模型请求结算", RELEASE: "释放预占额度", ADJUSTMENT: "额度调整" }[type];
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
