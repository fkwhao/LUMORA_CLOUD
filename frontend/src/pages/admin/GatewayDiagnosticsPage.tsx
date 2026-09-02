import { Button, Card, Chip } from "@heroui/react";
import { Activity, CircleGauge, Clock3, RefreshCw, TriangleAlert } from "lucide-react";
import { useEffect, useState } from "react";

import { ApiClientError } from "../../api/auth";
import { getGatewayDiagnostics, type GatewayDiagnostics } from "../../api/gateway";

export function GatewayDiagnosticsPage() {
  const [data, setData] = useState<GatewayDiagnostics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true); setError(null);
    try { setData(await getGatewayDiagnostics(100)); }
    catch (reason) { setError(reason instanceof ApiClientError ? reason.message : "无法读取网关诊断"); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  const summary = data?.summary;
  const successRate = summary?.total ? (summary.succeeded / summary.total) * 100 : 0;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div><p className="mb-1 text-sm text-muted">模型调用链路</p><h1 className="text-2xl font-semibold tracking-tight">网关诊断</h1><p className="mt-2 text-sm text-muted">仅保存追踪标识、模型、供应商、状态与耗时；不记录 Prompt、响应正文或 API Key。</p></div><Button isDisabled={loading} onPress={() => void load()} variant="secondary"><RefreshCw size={16} />刷新</Button></header>
      {error && <div className="rounded-xl bg-danger-soft px-4 py-3 text-sm text-danger">{error}</div>}
      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Metric icon={Activity} label="窗口请求" value={summary ? String(summary.total) : "—"} />
        <Metric icon={CircleGauge} label="成功率" value={summary ? `${successRate.toFixed(1)}%` : "—"} />
        <Metric icon={Clock3} label="平均耗时" value={summary ? duration(summary.averageDurationMillis) : "—"} />
        <Metric icon={TriangleAlert} label="失败 / 运行中" value={summary ? `${summary.failed} / ${summary.running}` : "—"} />
      </section>
      <Card variant="default">
        <Card.Header><div><Card.Title>最近请求</Card.Title><Card.Description>{summary ? `${summary.window} 统计窗口 · P95 ${duration(summary.p95DurationMillis)}` : "等待数据"}</Card.Description></div></Card.Header>
        <Card.Content className="gap-0 pt-1">
          {loading ? <p className="py-12 text-center text-sm text-muted">正在读取诊断数据…</p> : !data?.records.length ? <p className="py-12 text-center text-sm text-muted">暂无模型请求记录</p> : data.records.map((record) => (
            <div className="grid gap-3 border-b border-separator py-4 last:border-0 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,.8fr)_minmax(0,.8fr)_auto] lg:items-center" key={record.traceId}>
              <div className="min-w-0"><p className="truncate text-sm font-medium">{record.modelCode || "未解析模型"}</p><p className="truncate text-xs text-muted">{record.traceId}</p></div>
              <div><p className="text-sm">{record.providerCode || "供应商解析未完成"}</p><p className="text-xs text-muted">用户 #{record.userId} · {record.protocol}</p></div>
              <div><p className="text-sm tabular-nums">{record.completedAt ? duration(record.durationMillis) : "进行中"}</p><p className="text-xs text-muted">{formatDate(record.startedAt)}{record.upstreamStatus ? ` · HTTP ${record.upstreamStatus}` : ""}</p></div>
              <div className="lg:text-right"><Status status={record.status} /><p className="mt-1 text-xs text-danger">{record.errorCode}</p></div>
            </div>
          ))}
        </Card.Content>
      </Card>
    </div>
  );
}

function Metric({ icon: Icon, label, value }: { icon: typeof Activity; label: string; value: string }) { return <Card variant="default"><Card.Content className="gap-3"><Icon className="text-muted" size={19} /><div><p className="text-sm text-muted">{label}</p><strong className="mt-1 block text-2xl font-semibold tabular-nums">{value}</strong></div></Card.Content></Card>; }
function Status({ status }: { status: "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELED" }) { const config = { RUNNING: ["运行中", "warning"], SUCCEEDED: ["成功", "success"], FAILED: ["失败", "danger"], CANCELED: ["已取消", "default"] }[status] as [string, "warning" | "success" | "danger" | "default"]; return <Chip color={config[1]} size="sm" variant="soft">{config[0]}</Chip>; }
function duration(value: number) { return value >= 1000 ? `${(value / 1000).toFixed(2)} 秒` : `${value} ms`; }
function formatDate(value: string) { return new Intl.DateTimeFormat("zh-CN", { dateStyle: "short", timeStyle: "medium" }).format(new Date(value)); }
