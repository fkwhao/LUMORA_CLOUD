import { Accordion, Alert, Button, Card, Chip, Input, Label, TextField } from "@heroui/react";
import { useState } from "react";
import { ApiClientError, apiFetch } from "../api/auth";
import type { SettlementEvidence } from "../api/reconciliation";

interface QueueEntry {
  command: { requestId: string; operation: string; attempts: number; createdAt: string; settlement?: SettlementEvidence; reason?: string };
  dueAt: string; blockedReason?: string; retryNote?: string;
}
interface QueuePage { items: QueueEntry[]; total: number; page: number; pageSize: number }
const base = "/api/admin/model-gateway/recovery";
export function BillingRecoveryQueue() {
  const [data, setData] = useState<QueuePage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reason, setReason] = useState("");
  const [confirmId, setConfirmId] = useState<string | null>(null);
  async function load(page = 1) {
    setBusy(true); setError(null); setConfirmId(null);
    try { setData(await apiFetch<QueuePage>(`${base}?page=${page}`)); }
    catch (error) { setError(message(error)); }
    finally { setBusy(false); }
  }
  async function retry(id: string) {
    setBusy(true); setError(null);
    try {
      await apiFetch<void>(`${base}/${encodeURIComponent(id)}/retry`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ reason: reason.trim() }) });
      await load(data?.page ?? 1);
    } catch (error) { setError(message(error)); }
    finally { setBusy(false); }
  }
  return <Card variant="default">
    <Card.Header className="flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><Card.Title>待恢复投递</Card.Title><Card.Description>查看本网关实例尚未确认送达的计费记录，包括达到重试上限后保留的证据。</Card.Description></div><Button size="sm" variant="secondary" isDisabled={busy} onPress={() => void load(data?.page ?? 1)}>{busy ? "正在读取…" : data ? "刷新恢复记录" : "查看恢复记录"}</Button></Card.Header>
    <Card.Content className="gap-4">
      {error && <Alert status="danger" role="alert"><Alert.Indicator /><Alert.Content className="min-w-0"><Alert.Title className="break-words">{error}</Alert.Title></Alert.Content></Alert>}
      {data && <p className="text-xs text-muted">共 {data.total} 条 · 第 {data.page} 页</p>}
      {data?.items.length === 0 && <p className="py-3 text-sm text-muted">当前实例没有等待投递的计费记录。</p>}
      {!!data?.items.length && <TextField variant="secondary" fullWidth isRequired isDisabled={busy}>
        <Label>恢复投递依据</Label>
        <Input fullWidth value={reason} maxLength={160} onChange={event => { setReason(event.target.value); setConfirmId(null); }} placeholder="填写已核对的请求记录、故障处理结果等" />
      </TextField>}
      {data?.items.map(entry => <div className="space-y-2 border-t border-separator pt-4" key={entry.command.requestId}>
        <div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0 flex-1"><p className="break-all text-sm">{entry.command.requestId}</p><p className="mt-1 text-xs text-muted">{({ SETTLE: "结算", RELEASE: "释放", PENDING: "标记待核对" } as Record<string, string>)[entry.command.operation]} · 已尝试 {entry.command.attempts} 次</p></div><Chip size="sm" variant="soft" color={entry.blockedReason ? "warning" : "default"}>{entry.blockedReason ? "需人工恢复" : "等待自动重试"}</Chip></div>
        {entry.blockedReason && <p className="text-sm text-warning">{entry.blockedReason}</p>}
        {entry.retryNote && <p className="break-words text-xs text-muted">{entry.retryNote}</p>}
        <Accordion hideSeparator><Accordion.Item id="evidence"><Accordion.Heading><Accordion.Trigger className="py-2 text-sm">查看用量依据<Accordion.Indicator /></Accordion.Trigger></Accordion.Heading><Accordion.Panel><Accordion.Body className="px-0 text-xs text-muted">{entry.command.settlement ? <dl className="mt-2 grid gap-2 break-all sm:grid-cols-2"><div>用量标识：{entry.command.settlement.usageId}</div><div>价格版本：{entry.command.settlement.pricingVersion}</div><div>费用：{entry.command.settlement.billedQuota}</div><div>发生时间：{entry.command.settlement.occurredAt}</div><div>输入 / 输出 / 推理 Token：{entry.command.settlement.inputTokens} / {entry.command.settlement.outputTokens} / {entry.command.settlement.reasoningTokens}</div><div>缓存读取 / 写入 Token：{entry.command.settlement.cacheReadTokens} / {entry.command.settlement.cacheWriteTokens}</div></dl> : <p className="mt-2">{entry.command.reason || "没有用量记录，需核对供应商依据"}</p>}</Accordion.Body></Accordion.Panel></Accordion.Item></Accordion>
        {confirmId === entry.command.requestId ? <Alert status="warning"><Alert.Indicator /><Alert.Content className="min-w-0 space-y-3"><Alert.Title className="break-words">按原有证据重新投递。依据：{reason}</Alert.Title><div className="flex flex-wrap gap-2"><Button size="sm" variant="primary" isDisabled={busy} onPress={() => void retry(entry.command.requestId)}>确认恢复投递</Button><Button size="sm" variant="tertiary" isDisabled={busy} onPress={() => setConfirmId(null)}>返回核对</Button></div></Alert.Content></Alert> : <Button size="sm" variant="tertiary" isDisabled={busy || !reason.trim()} onPress={() => setConfirmId(entry.command.requestId)}>恢复投递</Button>}
      </div>)}
      {data && data.total > data.pageSize && <div className="flex justify-end gap-2"><Button size="sm" variant="tertiary" isDisabled={busy || data.page <= 1} onPress={() => void load(data.page - 1)}>上一页</Button><Button size="sm" variant="tertiary" isDisabled={busy || data.page * data.pageSize >= data.total} onPress={() => void load(data.page + 1)}>下一页</Button></div>}
    </Card.Content>
  </Card>;
}
function message(error: unknown) { return error instanceof ApiClientError ? error.message : "暂时无法访问恢复记录，请稍后重试。"; }
