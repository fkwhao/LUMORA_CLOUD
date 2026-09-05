import { Alert, Button, Card, Checkbox, Chip, Input, Label, ListBox, Select, TextArea, TextField } from "@heroui/react";
import { ArrowLeft, ChevronLeft, ChevronRight, RefreshCw, Search } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { ApiClientError } from "../../api/auth";
import { BillingRecoveryQueue } from "../../components/BillingRecoveryQueue";
import {
  batchReconciliation, getReconciliation, listReconciliation, resolveReconciliation,
  type BatchResult, type ReconciliationCase, type ReconciliationPage, type Resolution, type SettlementEvidence,
} from "../../api/reconciliation";

const tokenFields = [["inputTokens", "输入 Token"], ["outputTokens", "输出 Token"], ["reasoningTokens", "推理 Token"], ["cacheReadTokens", "缓存读取 Token"], ["cacheWriteTokens", "缓存写入 Token"]] as const;

export function BillingReconciliationPage() {
  const [data, setData] = useState<ReconciliationPage | null>(null);
  const [status, setStatus] = useState("PENDING_RECONCILIATION");
  const [requestId, setRequestId] = useState("");
  const [userId, setUserId] = useState("");
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [batchReason, setBatchReason] = useState("");
  const [batchAction, setBatchAction] = useState<Resolution["action"]>("SETTLE");
  const [confirmBatch, setConfirmBatch] = useState(false);
  const [working, setWorking] = useState(false);
  const [results, setResults] = useState<BatchResult[]>([]);
  const sequence = useRef(0);

  async function load(nextPage = page) {
    const request = ++sequence.current;
    setLoading(true); setError(null); setConfirmBatch(false); setSelected([]);
    try {
      const value = await listReconciliation(status, requestId, userId, nextPage);
      if (request !== sequence.current) return;
      setData(value); setPage(nextPage); setSelected([]);
    } catch (reason) { if (request === sequence.current) setError(message(reason)); }
    finally { if (request === sequence.current) setLoading(false); }
  }
  useEffect(() => { void load(1); return () => { sequence.current += 1; }; }, []);

  async function runBatch() {
    setWorking(true); setError(null); setConfirmBatch(false);
    try {
      const value = await batchReconciliation(selected, batchAction, batchReason.trim());
      setResults(value);
      await load(page);
    } catch (reason) { setError(message(reason)); }
    finally { setWorking(false); }
  }

  if (detailId) return <CaseDetail id={detailId} onBack={() => { setDetailId(null); void load(page); }} />;

  return <div className="space-y-6">
    <header className="flex flex-wrap items-end justify-between gap-4"><div><p className="mb-1 text-sm text-muted">异常账务处理</p><h1 className="text-2xl font-semibold">结算对账</h1><p className="mt-2 text-sm text-muted">系统定期核对已有用量；缺少依据或额度不足的记录保留待处理。</p></div><Button isDisabled={loading || working} onPress={() => void load()} variant="secondary"><RefreshCw size={16} />刷新</Button></header>
    {error && <Feedback text={error} />}
    <Card variant="default"><Card.Content>
      <form className="grid items-end gap-3 sm:grid-cols-2 xl:grid-cols-[12rem_minmax(0,1fr)_9rem_auto]" onSubmit={event => { event.preventDefault(); void load(1); }}>
        <Select className="min-w-0" fullWidth isDisabled={working} selectedKey={status} onSelectionChange={key => { if (key != null) setStatus(String(key)); }} variant="secondary">
          <Label>状态</Label>
          <Select.Trigger className="min-h-10"><Select.Value /><Select.Indicator /></Select.Trigger>
          <Select.Popover><ListBox>
            <ListBox.Item id="PENDING_RECONCILIATION" textValue="待对账">待对账<ListBox.ItemIndicator /></ListBox.Item>
            <ListBox.Item id="SETTLED" textValue="已结算">已结算<ListBox.ItemIndicator /></ListBox.Item>
            <ListBox.Item id="RELEASED" textValue="已释放">已释放<ListBox.ItemIndicator /></ListBox.Item>
            <ListBox.Item id="ALL" textValue="全部">全部<ListBox.ItemIndicator /></ListBox.Item>
          </ListBox></Select.Popover>
        </Select>
        <TextField variant="secondary" className="min-w-0" fullWidth isDisabled={working}>
          <Label>请求 ID</Label>
          <Input fullWidth value={requestId} maxLength={64} onChange={event => setRequestId(event.target.value)} placeholder="按完整请求 ID 查询" />
        </TextField>
        <TextField variant="secondary" className="min-w-0" fullWidth isDisabled={working} type="number">
          <Label>用户 ID</Label>
          <Input fullWidth min="1" step="1" value={userId} onChange={event => setUserId(event.target.value)} placeholder="全部用户" />
        </TextField>
        <Button className="min-h-10" type="submit" isDisabled={loading || working} variant="primary"><Search size={16} />查询</Button>
      </form>
    </Card.Content></Card>
    <Card variant="default">
      <Card.Header><div><Card.Title>对账记录</Card.Title><Card.Description>{data ? `共 ${data.total} 条 · 第 ${data.page} 页` : "正在读取"}</Card.Description></div></Card.Header>
      <Card.Content className="gap-0 pt-1">
        {loading ? <p className="py-10 text-center text-sm text-muted">正在读取对账记录…</p> : !data?.items.length ? <p className="py-10 text-center text-sm text-muted">没有符合条件的记录。</p> : data.items.map(item => {
          const row = item.reservation;
          return <div className="grid items-start gap-3 border-b border-separator py-4 last:border-0 sm:grid-cols-[1.25rem_minmax(0,1fr)] xl:grid-cols-[1.25rem_minmax(0,1.4fr)_minmax(0,1fr)_7rem_5rem]" key={row.requestId}>
            <Checkbox variant="secondary" className="mt-1" isDisabled={working || row.status !== "PENDING_RECONCILIATION"} isSelected={selected.includes(row.requestId)} onChange={checked => { setConfirmBatch(false); setSelected(ids => checked ? [...ids, row.requestId] : ids.filter(id => id !== row.requestId)); }}>
              <Checkbox.Content><Checkbox.Control><Checkbox.Indicator /></Checkbox.Control><Label className="sr-only">选择 {row.requestId}</Label></Checkbox.Content>
            </Checkbox>
            <div className="min-w-0"><p className="truncate text-sm font-medium" title={row.modelCode}>{row.modelCode} · 用户 #{row.userId}</p><p className="mt-1 break-all text-xs text-muted">{row.requestId}</p><p className="mt-1 text-xs text-muted">{date(row.createdAt)}</p></div>
            <div className="min-w-0 text-xs text-muted"><p>{item.usage ? `已有用量 · 费用 ${quota(item.usage.billedQuota)}` : "缺少用量依据"}</p><p className="mt-1 break-words">{row.reconciliationNote || row.failureReason || "—"}</p><p className="mt-1">{row.holdReleased ? "超时占用额度已返还" : `预占 ${quota(row.requestedQuota)}`}</p></div>
            <Status status={row.status} />
            <Button size="sm" variant="tertiary" isDisabled={working} onPress={() => setDetailId(row.requestId)}>查看</Button>
          </div>;
        })}
        <div className="mt-4 flex items-center justify-end gap-2"><Button aria-label="上一页" isIconOnly size="sm" variant="tertiary" isDisabled={loading || working || page <= 1} onPress={() => void load(page - 1)}><ChevronLeft size={16} /></Button><span className="text-xs text-muted">{page} / {Math.max(1, Math.ceil((data?.total ?? 0) / 20))}</span><Button aria-label="下一页" isIconOnly size="sm" variant="tertiary" isDisabled={loading || working || !data || page * data.pageSize >= data.total} onPress={() => void load(page + 1)}><ChevronRight size={16} /></Button></div>
      </Card.Content>
    </Card>
    {!!selected.length && <Card variant="default"><Card.Header><Card.Title>批量处理 · 已选 {selected.length} 条</Card.Title></Card.Header><Card.Content className="gap-4">
      <p className="text-sm text-muted">批量结算使用已记录的用量；没有依据的记录需单独核对。每条记录分别返回结果。</p>
      <div className="grid items-end gap-3 sm:grid-cols-[12rem_minmax(0,1fr)]">
        <Select className="min-w-0" fullWidth isDisabled={working} selectedKey={batchAction} onSelectionChange={key => { if (key != null) { setBatchAction(String(key) as Resolution["action"]); setConfirmBatch(false); } }} variant="secondary">
          <Label>处理方式</Label>
          <Select.Trigger className="min-h-10"><Select.Value /><Select.Indicator /></Select.Trigger>
          <Select.Popover><ListBox>
            <ListBox.Item id="SETTLE" textValue="按已有用量结算">按已有用量结算<ListBox.ItemIndicator /></ListBox.Item>
            <ListBox.Item id="RELEASE" textValue="核实后释放">核实后释放<ListBox.ItemIndicator /></ListBox.Item>
          </ListBox></Select.Popover>
        </Select>
        <TextField variant="secondary" className="min-w-0" fullWidth isDisabled={working} isRequired>
          <Label>核对依据</Label>
          <Input fullWidth value={batchReason} maxLength={160} placeholder="填写账单编号、核对结果等，必填" onChange={event => { setBatchReason(event.target.value); setConfirmBatch(false); }} />
        </TextField>
      </div>
      {confirmBatch ? <Alert status="warning"><Alert.Indicator /><Alert.Content className="min-w-0 space-y-3"><Alert.Title className="break-words">将{batchAction === "SETTLE" ? "结算所选记录的已记录费用" : "关闭所选记录并放弃其中的争议费用"}。依据：{batchReason}</Alert.Title><div className="flex flex-wrap gap-2"><Button isDisabled={working} onPress={() => void runBatch()} variant="primary">确认处理 {selected.length} 条</Button><Button isDisabled={working} onPress={() => setConfirmBatch(false)} variant="tertiary">返回核对</Button></div></Alert.Content></Alert> : <Button className="self-start" isDisabled={working || !batchReason.trim()} onPress={() => setConfirmBatch(true)} variant="secondary">{working ? "正在处理…" : "核对批量操作"}</Button>}
    </Card.Content></Card>}
    <BillingRecoveryQueue />
    {!!results.length && <Card variant="default"><Card.Header><Card.Title>本次处理结果</Card.Title></Card.Header><Card.Content className="gap-3" aria-live="polite">{results.map(result => <div className="min-w-0 text-sm" key={result.requestId}><p className="break-all text-xs text-muted">{result.requestId}</p><p className={result.completed ? "text-success" : "text-danger"}>{result.completed ? statusLabel(result.status ?? "") : result.message}</p></div>)}</Card.Content></Card>}
  </div>;
}

function CaseDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const [data, setData] = useState<ReconciliationCase | null>(null);
  const [draft, setDraft] = useState<SettlementEvidence | null>(null);
  const [action, setAction] = useState<Resolution["action"]>("SETTLE");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<Resolution | null>(null);

  useEffect(() => {
    let active = true;
    getReconciliation(id).then(value => {
      if (!active) return;
      setData(value);
      setDraft(value.usage ? evidence(value.usage) : { usageId: "", pricingVersion: value.reservation.pricingVersion, inputTokens: 0, outputTokens: 0, reasoningTokens: 0, cacheReadTokens: 0, cacheWriteTokens: 0, billedQuota: 0, occurredAt: "" });
    }).catch(error => { if (active) setError(message(error)); });
    return () => { active = false; };
  }, [id]);

  function prepare() {
    setError(null);
    if (!reason.trim()) { setError("请填写核对依据。"); return; }
    if (action === "SETTLE" && (!draft?.usageId.trim() || !draft.occurredAt || !Number.isFinite(Date.parse(draft.occurredAt)))) { setError("请填写可靠用量标识和发生时间。"); return; }
    setConfirm({ action, reason: reason.trim(), ...(action === "SETTLE" && draft ? { settlement: evidence(draft) } : {}) });
  }
  async function resolve() {
    if (!confirm) return;
    setBusy(true); setError(null);
    try { setData(await resolveReconciliation(id, confirm)); setConfirm(null); setSuccess(true); }
    catch (error) { setError(message(error)); }
    finally { setBusy(false); }
  }

  return <div className="space-y-6">
    <Button isDisabled={busy} size="sm" variant="ghost" onPress={onBack}><ArrowLeft size={16} />返回对账列表</Button>
    {error && <Feedback text={error} />}
    {success && <Alert status="success" role="status"><Alert.Indicator /><Alert.Content><Alert.Title>处理完成，结果与审计记录已保存。</Alert.Title></Alert.Content></Alert>}
    {!data || !draft ? <p className="py-10 text-center text-sm text-muted">{error ? "无法读取记录，请返回列表重试。" : "正在读取依据…"}</p> : <>
      <Card variant="default"><Card.Header><div className="min-w-0"><Card.Title>{data.reservation.modelCode}</Card.Title><Card.Description className="break-all">{id}</Card.Description></div></Card.Header><Card.Content className="gap-4">
        <Status status={data.reservation.status} />
        <dl className="grid gap-4 text-sm sm:grid-cols-2 xl:grid-cols-4"><Meta label="用户" value={`#${data.reservation.userId}`} /><Meta label="原预占额度" value={quota(data.reservation.requestedQuota)} /><Meta label="记录费用" value={data.usage ? quota(data.usage.billedQuota) : "暂无可靠用量"} /><Meta label="额度占用" value={data.reservation.holdReleased ? "超时占用已返还" : data.reservation.status === "PENDING_RECONCILIATION" ? "仍有预占" : "已收尾"} /><Meta label="原始原因" value={data.reservation.failureReason || "—"} /><Meta label="最近核对" value={date(data.reservation.reconciliationCheckedAt)} /><Meta label="自动核对次数" value={String(data.reservation.reconciliationAttempts)} /><Meta label="核对结果" value={data.reservation.reconciliationNote || "—"} /></dl>
      </Card.Content></Card>
      {data.reservation.status === "PENDING_RECONCILIATION" && <Card variant="default"><Card.Header><div><Card.Title>核实处理</Card.Title><Card.Description>依据供应商账单或可靠用量记录填写。结算使用该请求原周期的可用额度。</Card.Description></div></Card.Header><Card.Content>
        <form className="space-y-4" onSubmit={event => { event.preventDefault(); prepare(); }}>
          <fieldset disabled={busy || !!confirm} className="space-y-4">
            <Select fullWidth isDisabled={busy || !!confirm} selectedKey={action} onSelectionChange={key => { if (key != null) setAction(String(key) as Resolution["action"]); }} variant="secondary">
              <Label>处理方式</Label>
              <Select.Trigger className="min-h-10"><Select.Value /><Select.Indicator /></Select.Trigger>
              <Select.Popover><ListBox>
                <ListBox.Item id="SETTLE" textValue="补结算">补结算<ListBox.ItemIndicator /></ListBox.Item>
                <ListBox.Item id="RELEASE" textValue="核实后释放并关闭">核实后释放并关闭<ListBox.ItemIndicator /></ListBox.Item>
              </ListBox></Select.Popover>
            </Select>
            {action === "SETTLE" && <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              <TextField variant="secondary" className="min-w-0" fullWidth isRequired isDisabled={busy || !!confirm} isReadOnly={!!data.usage}>
                <Label>用量标识</Label><Input fullWidth value={draft.usageId} maxLength={64} onChange={event => setDraft({ ...draft, usageId: event.target.value })} />
              </TextField>
              <TextField variant="secondary" className="min-w-0" fullWidth isReadOnly isDisabled={busy || !!confirm}>
                <Label>价格版本</Label><Input fullWidth value={draft.pricingVersion} />
              </TextField>
              <TextField variant="secondary" className="min-w-0" fullWidth isRequired type="number" isDisabled={busy || !!confirm} isReadOnly={!!data.usage}>
                <Label>实际费用</Label><Input fullWidth min="0" step="0.000001" value={draft.billedQuota} onChange={event => setDraft({ ...draft, billedQuota: Number(event.target.value) })} />
              </TextField>
              {tokenFields.map(([key, label]) => <TextField variant="secondary" className="min-w-0" fullWidth isRequired type="number" isDisabled={busy || !!confirm} isReadOnly={!!data.usage} key={key}>
                <Label>{label}</Label><Input fullWidth min="0" step="1" value={draft[key]} onChange={event => setDraft({ ...draft, [key]: Number(event.target.value) })} />
              </TextField>)}
              <TextField variant="secondary" className="min-w-0" fullWidth isRequired isDisabled={busy || !!confirm} isReadOnly={!!data.usage}>
                <Label>用量发生时间</Label><Input fullWidth placeholder="2026-09-05T12:30:00+08:00" value={draft.occurredAt} onChange={event => setDraft({ ...draft, occurredAt: event.target.value })} />
              </TextField>
            </div>}
            <TextField variant="secondary" fullWidth isRequired isDisabled={busy || !!confirm}>
              <Label>核对依据</Label><TextArea fullWidth rows={3} maxLength={160} value={reason} onChange={event => setReason(event.target.value)} placeholder="例如供应商账单编号、用量来源和处理原因" />
            </TextField>
          </fieldset>
          {confirm ? <Alert status="warning"><Alert.Indicator /><Alert.Content className="min-w-0 space-y-3"><Alert.Title className="break-words">{confirm.action === "SETTLE" ? `确认结算 ${quota(confirm.settlement!.billedQuota)} 额度` : "确认关闭该记录并放弃争议费用"}。依据：{confirm.reason}</Alert.Title><div className="flex flex-wrap gap-2"><Button isDisabled={busy} onPress={() => void resolve()} variant="primary">{busy ? "正在保存…" : "确认处理"}</Button><Button isDisabled={busy} variant="tertiary" onPress={() => setConfirm(null)}>返回核对</Button></div></Alert.Content></Alert> : <Button type="submit" variant="primary">核对操作</Button>}
        </form>
      </Card.Content></Card>}
      <Card variant="default"><Card.Header><Card.Title>处理与审计记录</Card.Title></Card.Header><Card.Content className="gap-0">{data.history?.length ? data.history.map(entry => <div className="border-b border-separator py-3 last:border-0" key={entry.id}><p className="break-words text-sm">{entry.description}</p><p className="mt-1 text-xs text-muted">{date(entry.createdAt)}</p></div>) : <p className="py-4 text-sm text-muted">暂无审计记录。</p>}</Card.Content></Card>
    </>}
  </div>;
}

function evidence(value: SettlementEvidence): SettlementEvidence { return { usageId: value.usageId, pricingVersion: value.pricingVersion, inputTokens: value.inputTokens, outputTokens: value.outputTokens, reasoningTokens: value.reasoningTokens, cacheReadTokens: value.cacheReadTokens, cacheWriteTokens: value.cacheWriteTokens, billedQuota: value.billedQuota, occurredAt: value.occurredAt }; }
function statusLabel(status: string) { return ({ PENDING_RECONCILIATION: "待对账", SETTLED: "已结算", RELEASED: "已释放" } as Record<string, string>)[status] || status; }
function Status({ status }: { status: string }) { return <Chip className="self-start" size="sm" variant="soft" color={status === "SETTLED" ? "success" : status === "PENDING_RECONCILIATION" ? "warning" : "default"}>{statusLabel(status)}</Chip>; }
function Meta({ label, value }: { label: string; value: string }) { return <div className="min-w-0"><dt className="text-xs text-muted">{label}</dt><dd className="mt-1 break-words">{value}</dd></div>; }
function Feedback({ text }: { text: string }) { return <Alert status="danger" role="alert"><Alert.Indicator /><Alert.Content className="min-w-0"><Alert.Title className="break-words">{text}</Alert.Title></Alert.Content></Alert>; }
function quota(value: number) { return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 6 }).format(value); }
function date(value?: string) { return value ? new Intl.DateTimeFormat("zh-CN", { dateStyle: "short", timeStyle: "medium" }).format(new Date(value)) : "—"; }
function message(error: unknown) { return error instanceof ApiClientError ? error.message : "暂时无法完成对账操作，请刷新核对后重试。"; }
