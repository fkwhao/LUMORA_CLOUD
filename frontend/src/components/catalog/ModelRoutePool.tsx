import { Button, Card, Checkbox, Chip, Input, Label, ListBox, Select, TextField } from "@heroui/react";
import { GitBranch, PencilLine, Plus, Trash2 } from "lucide-react";
import { useState, type FormEvent } from "react";

import { ApiClientError } from "../../api/auth";
import {
  createModelRoute,
  deleteModelRoute,
  updateModelRoute,
  type AdminModel,
  type CostTimePricingRule,
  type ModelProvider,
  type ModelRoute,
  type ModelRouteInput,
  type PricingDay,
} from "../../api/catalog";

const days: Array<{ id: PricingDay; label: string }> = [
  { id: "MONDAY", label: "周一" }, { id: "TUESDAY", label: "周二" },
  { id: "WEDNESDAY", label: "周三" }, { id: "THURSDAY", label: "周四" },
  { id: "FRIDAY", label: "周五" }, { id: "SATURDAY", label: "周六" },
  { id: "SUNDAY", label: "周日" },
];

type EditableRule = CostTimePricingRule & { id: string };

interface RouteFieldState {
  routeName: string;
  upstreamModel: string;
  costCurrency: string;
  priority: string;
  weight: string;
  maxConcurrency: string;
  requestsPerMinute: string;
  tokensPerMinute: string;
  inputCost: string;
  cacheReadCost: string;
  cacheWriteCost: string;
  outputCost: string;
}

export function ModelRoutePool({ model, providers, onChanged }: {
  model: AdminModel;
  providers: ModelProvider[];
  onChanged: () => Promise<void>;
}) {
  const routes = model.draft?.routes ?? [];
  const [editing, setEditing] = useState<ModelRoute | "new" | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function remove(route: ModelRoute) {
    if (!window.confirm(`确认删除路由“${route.routeName}”？`)) return;
    setPending(true); setError(null);
    try {
      await deleteModelRoute(model.modelId, route);
      setEditing(null);
      await onChanged();
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(false);
    }
  }

  return (
    <Card variant="default">
      <Card.Header className="flex-row items-start justify-between gap-4">
        <div>
          <Card.Title>上游路由池</Card.Title>
          <Card.Description>同一逻辑模型可绑定多个供应商账号，并按优先级与权重分流。</Card.Description>
        </div>
        <Button isDisabled={pending} onPress={() => setEditing("new")} size="sm" variant="secondary">
          <Plus size={15} /> 添加路由
        </Button>
      </Card.Header>
      <Card.Content className="gap-4">
        {error && <div className="rounded-xl bg-danger-soft px-3 py-2 text-xs text-danger" role="alert">{error}</div>}
        <p className="text-xs leading-5 text-muted">
          优先级数值越小越先使用；同优先级按权重随机分配。账号容量作用于该账号的所有路由，路由容量只作用于当前绑定。
        </p>
        <div className="space-y-3">
          {routes.map((route) => (
            <div className="rounded-xl border border-border bg-default/30 p-4" key={route.id}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-medium">{route.routeName}</p>
                    {route.primary && <Chip color="accent" size="sm" variant="soft">默认</Chip>}
                    <Chip color={route.status === "ACTIVE" ? "success" : "default"} size="sm" variant="soft">
                      {route.status === "ACTIVE" ? "启用" : "停用"}
                    </Chip>
                  </div>
                  <p className="mt-1 break-all text-xs text-muted">{route.providerName} · {route.upstreamModel}</p>
                </div>
                <div className="flex gap-1">
                  <Button aria-label={`编辑${route.primary ? "默认" : "备用"}路由`} isIconOnly onPress={() => setEditing(route)} size="sm" variant="tertiary"><PencilLine size={15} /></Button>
                  {!route.primary && (
                    <Button aria-label="删除路由" isDisabled={pending} isIconOnly onPress={() => void remove(route)} size="sm" variant="tertiary"><Trash2 size={15} /></Button>
                  )}
                </div>
              </div>
              <div className="mt-3 grid gap-2 text-xs text-muted sm:grid-cols-2">
                <p>优先级 / 权重：{route.priority} / {route.weight}</p>
                <p>协议：{protocolLabel(route.protocolType)}</p>
                <p>路由并发：{limit(route.maxConcurrency)}</p>
                <p>账号并发：{limit(route.accountMaxConcurrency)}</p>
                <p>路由 RPM / TPM：{limit(route.requestsPerMinute)} / {limit(route.tokensPerMinute)}</p>
                <p>故障转移 / 熔断：{route.failoverEnabled ? "开" : "关"} / {route.circuitBreakerEnabled ? "开" : "关"}</p>
              </div>
              {route.primary && <p className="mt-3 text-xs text-muted">默认路由的调度参数可单独编辑；供应商、模型 ID 和成本仍由上方模型草稿统一维护。</p>}
            </div>
          ))}
        </div>

        {editing && (
          <RouteEditor
            key={editing === "new" ? "new" : editing.id}
            current={editing === "new" ? undefined : editing}
            model={model}
            pending={pending}
            providers={providers.filter((provider) => provider.status === "ACTIVE")}
            onCancel={() => setEditing(null)}
            onError={setError}
            onPending={setPending}
            onSaved={async () => { setEditing(null); await onChanged(); }}
          />
        )}
      </Card.Content>
    </Card>
  );
}

function RouteEditor({ current, model, providers, pending, onCancel, onError, onPending, onSaved }: {
  current?: ModelRoute;
  model: AdminModel;
  providers: ModelProvider[];
  pending: boolean;
  onCancel: () => void;
  onError: (error: string | null) => void;
  onPending: (pending: boolean) => void;
  onSaved: () => Promise<void>;
}) {
  const draft = model.draft!;
  const primary = current?.primary ?? false;
  const [providerId, setProviderId] = useState(String(current?.providerId ?? providers[0]?.id ?? ""));
  const [fields, setFields] = useState<RouteFieldState>(() => routeFieldDefaults(current, draft));
  const [active, setActive] = useState(current?.status !== "DISABLED");
  const [failover, setFailover] = useState(current?.failoverEnabled ?? true);
  const [circuit, setCircuit] = useState(current?.circuitBreakerEnabled ?? true);
  const [timedCost, setTimedCost] = useState(Boolean(current?.costTimePricingPolicy));
  const [zoneId, setZoneId] = useState(current?.costTimePricingPolicy?.zoneId ?? "Asia/Shanghai");
  const [rules, setRules] = useState<EditableRule[]>(
    current?.costTimePricingPolicy?.rules.map((rule) => ({ ...rule, id: crypto.randomUUID() })) ?? [],
  );

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input: ModelRouteInput = {
      routeName: fields.routeName.trim(), providerId: Number(providerId), upstreamModel: fields.upstreamModel.trim(),
      priority: Number(fields.priority), weight: Number(fields.weight),
      maxConcurrency: optionalNumber(fields.maxConcurrency), requestsPerMinute: optionalNumber(fields.requestsPerMinute),
      tokensPerMinute: optionalNumber(fields.tokensPerMinute), failoverEnabled: failover,
      circuitBreakerEnabled: circuit, status: active ? "ACTIVE" : "DISABLED",
      costCurrency: fields.costCurrency.trim().toUpperCase(),
      uncachedInputCostPerMillion: Number(fields.inputCost), cachedInputCostPerMillion: Number(fields.cacheReadCost),
      cacheCreationInputCostPerMillion: optionalNumber(fields.cacheWriteCost), outputCostPerMillion: Number(fields.outputCost),
      costTimePricingPolicy: primary
        ? current?.costTimePricingPolicy ?? undefined
        : timedCost ? { zoneId, rules: rules.map(({ id: _, ...rule }) => rule) } : undefined,
    };
    onPending(true); onError(null);
    try {
      if (current) await updateModelRoute(model.modelId, current, input);
      else await createModelRoute(model.modelId, input);
      await onSaved();
    } catch (reason) {
      onError(message(reason));
    } finally {
      onPending(false);
    }
  }

  function addRule() {
    setRules((value) => [...value, {
      id: crypto.randomUUID(), name: `成本时段 ${value.length + 1}`, daysOfWeek: days.map((day) => day.id),
      startTime: "09:00", endTime: "12:00", costRates: {
        uncachedInputPerMillion: current?.costRates.uncachedInputPerMillion ?? draft.costRates.uncachedInputPerMillion,
        cachedInputPerMillion: current?.costRates.cachedInputPerMillion ?? draft.costRates.cachedInputPerMillion,
        cacheCreationInputPerMillion: current?.costRates.cacheCreationInputPerMillion ?? draft.costRates.cacheCreationInputPerMillion,
        outputPerMillion: current?.costRates.outputPerMillion ?? draft.costRates.outputPerMillion,
      },
    }]);
  }

  function updateField(name: keyof RouteFieldState, value: string) {
    setFields((currentFields) => ({ ...currentFields, [name]: value }));
  }

  return (
    <form className="space-y-4 rounded-xl border border-border bg-background/50 p-4" onSubmit={submit}>
      <div className="flex items-center gap-2 text-sm font-medium"><GitBranch size={16} /> {primary ? "编辑默认路由" : current ? "编辑备用路由" : "新增备用路由"}</div>
      {primary && (
        <div className="rounded-lg border border-border bg-default/30 px-3 py-2 text-xs leading-5 text-muted">
          当前编辑仅影响路由调度。供应商账号、上游模型 ID、协议与成本价格继续跟随模型草稿，避免产生两套配置。
        </div>
      )}
      {!primary && (
        <div className="grid gap-4 sm:grid-cols-2">
          <Field controlledValue={fields.routeName} label="路由名称" name="routeName" onChange={(value) => updateField("routeName", value)} placeholder="例如 DeepSeek 账号 B" required />
          <Select fullWidth isRequired name="providerId" onSelectionChange={(key) => key && setProviderId(String(key))} selectedKey={providerId} variant="secondary">
            <Label>供应商账号</Label><Select.Trigger><Select.Value>{({ selectedText }) => selectedText}</Select.Value><Select.Indicator /></Select.Trigger>
            <Select.Popover><ListBox>{providers.map((provider) => <ListBox.Item id={String(provider.id)} key={provider.id} textValue={provider.name}>{provider.name}<ListBox.ItemIndicator /></ListBox.Item>)}</ListBox></Select.Popover>
          </Select>
          <Field controlledValue={fields.upstreamModel} label="上游模型 ID" name="upstreamModel" onChange={(value) => updateField("upstreamModel", value)} required />
          <Field controlledValue={fields.costCurrency} label="成本币种" maxLength={3} name="costCurrency" onChange={(value) => updateField("costCurrency", value)} required />
        </div>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field controlledValue={fields.priority} label="优先级" min="0" name="priority" onChange={(value) => updateField("priority", value)} required type="number" />
        <Field controlledValue={fields.weight} label="权重" min="1" name="weight" onChange={(value) => updateField("weight", value)} required type="number" />
        <Field controlledValue={fields.maxConcurrency} label="路由并发（可选）" min="1" name="maxConcurrency" onChange={(value) => updateField("maxConcurrency", value)} placeholder="不限" type="number" />
        <Field controlledValue={fields.requestsPerMinute} label="路由 RPM（可选）" min="1" name="requestsPerMinute" onChange={(value) => updateField("requestsPerMinute", value)} placeholder="不限" type="number" />
        <Field controlledValue={fields.tokensPerMinute} label="路由 TPM（可选）" min="1" name="tokensPerMinute" onChange={(value) => updateField("tokensPerMinute", value)} placeholder="不限" type="number" />
      </div>

      {!primary && (
        <div className="grid gap-4 border-t border-separator pt-4 sm:grid-cols-2">
          <Field controlledValue={fields.inputCost} label="未缓存输入成本 / 百万 Token" min="0" name="inputCost" onChange={(value) => updateField("inputCost", value)} required type="number" />
          <Field controlledValue={fields.cacheReadCost} label="缓存命中成本 / 百万 Token" min="0" name="cacheReadCost" onChange={(value) => updateField("cacheReadCost", value)} required type="number" />
          <Field controlledValue={fields.cacheWriteCost} label="缓存创建成本（可选）" min="0" name="cacheWriteCost" onChange={(value) => updateField("cacheWriteCost", value)} type="number" />
          <Field controlledValue={fields.outputCost} label="输出成本（含推理）/ 百万 Token" min="0" name="outputCost" onChange={(value) => updateField("outputCost", value)} required type="number" />
        </div>
      )}

      <div className="flex flex-wrap gap-x-5 gap-y-2">
        <Toggle checked={active} label="启用路由" onChange={setActive} />
        <Toggle checked={failover} label="失败后尝试下一路由" onChange={setFailover} />
        <Toggle checked={circuit} label="启用动态熔断" onChange={setCircuit} />
        {!primary && <Toggle checked={timedCost} label="启用上游成本时段" onChange={setTimedCost} />}
      </div>

      {!primary && timedCost && (
        <div className="space-y-3 rounded-xl bg-default/30 p-4">
          <Field controlledValue={zoneId} label="成本时区" name="zoneId" onChange={setZoneId} required />
          {rules.map((rule) => <RuleEditor key={rule.id} rule={rule} onChange={(next) => setRules((all) => all.map((item) => item.id === rule.id ? next : item))} onRemove={() => setRules((all) => all.filter((item) => item.id !== rule.id))} />)}
          <Button onPress={addRule} size="sm" variant="secondary"><Plus size={14} /> 添加成本时段</Button>
          {rules.length === 0 && <p className="text-xs text-danger">启用后至少添加一条成本时段。</p>}
        </div>
      )}

      <div className="flex justify-end gap-2">
        <Button onPress={onCancel} variant="tertiary">取消</Button>
        <Button isDisabled={pending || !providerId || (timedCost && rules.length === 0)} type="submit" variant="primary">{pending ? "正在保存…" : "保存路由"}</Button>
      </div>
    </form>
  );
}

function RuleEditor({ rule, onChange, onRemove }: { rule: EditableRule; onChange: (rule: EditableRule) => void; onRemove: () => void }) {
  const updateRate = (key: keyof EditableRule["costRates"], value: string) => onChange({ ...rule, costRates: { ...rule.costRates, [key]: Number(value) } });
  return (
    <div className="space-y-3 rounded-xl border border-border p-3">
      <div className="flex items-start gap-2"><Field controlledValue={rule.name} label="规则名称" name="ruleName" onChange={(value) => onChange({ ...rule, name: value })} required /><Button aria-label="删除时段" className="mt-6" isIconOnly onPress={onRemove} size="sm" variant="tertiary"><Trash2 size={14} /></Button></div>
      <div className="flex flex-wrap gap-3">{days.map((day) => <Toggle checked={rule.daysOfWeek.includes(day.id)} key={day.id} label={day.label} onChange={(selected) => onChange({ ...rule, daysOfWeek: selected ? [...rule.daysOfWeek, day.id] : rule.daysOfWeek.filter((value) => value !== day.id) })} />)}</div>
      <div className="grid gap-3 sm:grid-cols-2"><Field controlledValue={rule.startTime.slice(0, 5)} label="开始时间" name="start" onChange={(value) => onChange({ ...rule, startTime: value })} required type="time" /><Field controlledValue={rule.endTime.slice(0, 5)} label="结束时间" name="end" onChange={(value) => onChange({ ...rule, endTime: value })} required type="time" /></div>
      <div className="grid gap-3 sm:grid-cols-2"><Field controlledValue={String(rule.costRates.uncachedInputPerMillion)} label="未缓存输入成本" min="0" name="ruleInput" onChange={(value) => updateRate("uncachedInputPerMillion", value)} required type="number" /><Field controlledValue={String(rule.costRates.cachedInputPerMillion)} label="缓存命中成本" min="0" name="ruleCached" onChange={(value) => updateRate("cachedInputPerMillion", value)} required type="number" /><Field controlledValue={String(rule.costRates.cacheCreationInputPerMillion ?? 0)} label="缓存创建成本" min="0" name="ruleCacheWrite" onChange={(value) => updateRate("cacheCreationInputPerMillion", value)} type="number" /><Field controlledValue={String(rule.costRates.outputPerMillion)} label="输出成本（含推理）" min="0" name="ruleOutput" onChange={(value) => updateRate("outputPerMillion", value)} required type="number" /></div>
    </div>
  );
}

function Toggle({ checked, label, onChange }: { checked: boolean; label: string; onChange: (value: boolean) => void }) {
  return <Checkbox isSelected={checked} onChange={onChange}><Checkbox.Content><Checkbox.Control><Checkbox.Indicator /></Checkbox.Control><Label>{label}</Label></Checkbox.Content></Checkbox>;
}

function Field({ name, label, type = "text", required = false, defaultValue, controlledValue, onChange, ...input }: {
  name: string; label: string; type?: string; required?: boolean; defaultValue?: string | number | null;
  controlledValue?: string; onChange?: (value: string) => void; min?: string; maxLength?: number; placeholder?: string;
}) {
  return <TextField className="flex-1" fullWidth isRequired={required} name={name} type={type}><Label>{label}</Label><Input {...input} defaultValue={controlledValue == null ? defaultValue ?? "" : undefined} fullWidth onChange={onChange ? (event) => onChange(event.target.value) : undefined} step={type === "number" ? "0.000001" : undefined} value={controlledValue} /></TextField>;
}

function routeFieldDefaults(current: ModelRoute | undefined, draft: NonNullable<AdminModel["draft"]>): RouteFieldState {
  const rates = current?.costRates ?? draft.costRates;
  return {
    routeName: current?.routeName ?? "",
    upstreamModel: current?.upstreamModel ?? draft.upstreamModel,
    costCurrency: current?.costCurrency ?? draft.costCurrency,
    priority: String(current?.priority ?? 100),
    weight: String(current?.weight ?? 100),
    maxConcurrency: editableNumber(current?.maxConcurrency),
    requestsPerMinute: editableNumber(current?.requestsPerMinute),
    tokensPerMinute: editableNumber(current?.tokensPerMinute),
    inputCost: String(rates.uncachedInputPerMillion),
    cacheReadCost: String(rates.cachedInputPerMillion),
    cacheWriteCost: editableNumber(rates.cacheCreationInputPerMillion),
    outputCost: String(rates.outputPerMillion),
  };
}

function editableNumber(value?: number | null): string { return value == null ? "" : String(value); }
function optionalNumber(value: string): number | undefined { const normalized = value.trim(); return normalized ? Number(normalized) : undefined; }
function limit(value?: number | null): string { return value == null ? "不限" : new Intl.NumberFormat("zh-CN").format(value); }
function protocolLabel(value: string): string { return ({ ANTHROPIC: "Anthropic Messages", OPENAI_COMPATIBLE: "Chat Completions", RESPONSES: "Responses" } as Record<string, string>)[value] ?? value; }
function message(reason: unknown): string { return reason instanceof ApiClientError ? reason.message : "无法更新模型路由，请稍后重试"; }
