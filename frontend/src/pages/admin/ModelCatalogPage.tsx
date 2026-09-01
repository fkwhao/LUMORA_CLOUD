import {
  Button,
  Card,
  Checkbox,
  Chip,
  Input,
  Label,
  ListBox,
  Select,
  TextArea,
  TextField,
} from "@heroui/react";
import {
  Archive,
  Boxes,
  CheckCircle2,
  ChevronDown,
  CircleDollarSign,
  Clock3,
  CloudUpload,
  CopyPlus,
  History,
  PencilLine,
  Plus,
  Power,
  RefreshCw,
  Server,
  SlidersHorizontal,
  Trash2,
} from "lucide-react";
import { useEffect, useMemo, useState, type FormEvent } from "react";

import { ApiClientError } from "../../api/auth";
import {
  createModel,
  createModelDraft,
  discardModelDraft,
  listModels,
  listModelVersions,
  listProviders,
  publishModelDraft,
  updateModelDraft,
  updateModelStatus,
  type AdminModel,
  type CostTimePricingPolicy,
  type CostTimePricingRule,
  type ModelProvider,
  type ModelRates,
  type ModelVersion,
  type ModelVersionInput,
  type PricingDay,
  type QuotaTimePricingPolicy,
  type QuotaTimePricingRule,
} from "../../api/catalog";

interface EditableCostTimePricingRule extends CostTimePricingRule {
  id: string;
  cacheCreationEnabled: boolean;
}

interface EditableQuotaTimePricingRule extends QuotaTimePricingRule {
  id: string;
}

interface ModelEditorSubmission {
  code: string;
  providerId: number;
  version: ModelVersionInput;
}

const pricingDays: Array<{ key: PricingDay; label: string }> = [
  { key: "MONDAY", label: "周一" },
  { key: "TUESDAY", label: "周二" },
  { key: "WEDNESDAY", label: "周三" },
  { key: "THURSDAY", label: "周四" },
  { key: "FRIDAY", label: "周五" },
  { key: "SATURDAY", label: "周六" },
  { key: "SUNDAY", label: "周日" },
];

type VersionRateKey = keyof Pick<
  ModelVersionInput,
  | "uncachedInputCostPerMillion"
  | "cachedInputCostPerMillion"
  | "outputCostPerMillion"
  | "uncachedInputQuotaPerMillion"
  | "cachedInputQuotaPerMillion"
  | "outputQuotaPerMillion"
  | "minimumRequestQuota"
>;

const costFields: Array<{ name: VersionRateKey; label: string }> = [
  { name: "uncachedInputCostPerMillion", label: "未缓存输入" },
  { name: "cachedInputCostPerMillion", label: "缓存命中输入" },
  { name: "outputCostPerMillion", label: "输出（含推理）" },
];

const quotaFields: Array<{ name: VersionRateKey; label: string }> = [
  { name: "uncachedInputQuotaPerMillion", label: "未缓存输入" },
  { name: "cachedInputQuotaPerMillion", label: "缓存命中输入" },
  { name: "outputQuotaPerMillion", label: "输出（含推理）" },
];

export function ModelCatalogPage() {
  const [models, setModels] = useState<AdminModel[]>([]);
  const [providers, setProviders] = useState<ModelProvider[]>([]);
  const [selectedModelId, setSelectedModelId] = useState<number | null>(null);
  const [historyModelId, setHistoryModelId] = useState<number | null>(null);
  const [versions, setVersions] = useState<Record<number, ModelVersion[]>>({});
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState<string | null>(null);
  const [newFormVersion, setNewFormVersion] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const selectedModel = useMemo(
    () => models.find((model) => model.modelId === selectedModelId) ?? null,
    [models, selectedModelId],
  );
  const activeProviders = providers.filter((provider) => provider.status === "ACTIVE");
  const editorProviders = providers.filter((provider) =>
    provider.status === "ACTIVE" || provider.id === selectedModel?.draft?.providerId,
  );

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [modelData, providerData] = await Promise.all([listModels(), listProviders()]);
      setModels(modelData);
      setProviders(providerData);
      setVersions({});
    } catch (reason) {
      setError(message(reason));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  function upsert(updated: AdminModel) {
    setModels((current) => {
      const exists = current.some((item) => item.modelId === updated.modelId);
      const next = exists
        ? current.map((item) => item.modelId === updated.modelId ? updated : item)
        : [...current, updated];
      return next.sort((left, right) => left.modelId - right.modelId);
    });
    setVersions((current) => {
      const next = { ...current };
      delete next[updated.modelId];
      return next;
    });
    setHistoryModelId((current) => current === updated.modelId ? null : current);
  }

  async function save({ code, providerId, version }: ModelEditorSubmission) {
    const operation = selectedModel ? `save:${selectedModel.modelId}` : "create";
    setPending(operation);
    clearFeedback();
    try {
      const updated = selectedModel
        ? await updateModelDraft(selectedModel, providerId, version)
        : await createModel({ code, providerId, version });
      upsert(updated);
      setSelectedModelId(updated.modelId);
      setNotice(selectedModel ? `${version.displayName} 的草稿已保存。` : `${version.displayName} 已创建为草稿。`);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  async function startDraft(model: AdminModel) {
    setPending(`draft:${model.modelId}`);
    clearFeedback();
    try {
      const updated = await createModelDraft(model.modelId);
      upsert(updated);
      setSelectedModelId(updated.modelId);
      setNotice(`${updated.draft?.displayName ?? updated.code} 的新草稿已创建。`);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  async function discardDraft(model: AdminModel) {
    const draft = model.draft;
    const published = model.published;
    if (!draft) return;
    const removesModel = !published;
    const confirmed = window.confirm(published
      ? `确认放弃 ${draft.displayName} v${draft.versionNo} 草稿？线上 v${published.versionNo} 不会受到影响。`
      : `确认放弃 ${draft.displayName} 的草稿？该模型尚未发布，放弃后整个未发布模型都会被删除。`);
    if (!confirmed) return;

    setPending(`discard:${model.modelId}`);
    clearFeedback();
    try {
      await discardModelDraft(model);
      if (selectedModelId === model.modelId) setSelectedModelId(null);
      if (historyModelId === model.modelId) setHistoryModelId(null);
      await load();
      setNotice(removesModel ? `${model.code} 的未发布模型已删除。` : `${model.code} 的草稿已放弃，线上版本保持不变。`);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  async function publish(model: AdminModel) {
    if (!model.draft || !window.confirm(`确认发布 ${model.draft.displayName} v${model.draft.versionNo}？`)) return;
    setPending(`publish:${model.modelId}`);
    clearFeedback();
    try {
      const updated = await publishModelDraft(model);
      upsert(updated);
      setSelectedModelId(null);
      setNotice(`${updated.published?.displayName ?? updated.code} 已发布，可供 Model Gateway 读取。`);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  async function toggleStatus(model: AdminModel) {
    const nextStatus = model.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
    setPending(`status:${model.modelId}`);
    clearFeedback();
    try {
      const updated = await updateModelStatus(model, nextStatus);
      upsert(updated);
      setNotice(`${updated.code} 已${nextStatus === "ACTIVE" ? "启用" : "停用"}。`);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  async function toggleHistory(model: AdminModel) {
    if (historyModelId === model.modelId) {
      setHistoryModelId(null);
      return;
    }
    setHistoryModelId(model.modelId);
    if (versions[model.modelId]) return;
    setPending(`history:${model.modelId}`);
    clearFeedback();
    try {
      const data = await listModelVersions(model.modelId);
      setVersions((current) => ({ ...current, [model.modelId]: data }));
    } catch (reason) {
      setHistoryModelId(null);
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  function clearFeedback() {
    setError(null);
    setNotice(null);
  }

  function beginCreate() {
    setSelectedModelId(null);
    setNewFormVersion((current) => current + 1);
    clearFeedback();
  }

  const publishedCount = models.filter((model) => model.published && model.status === "ACTIVE").length;
  const draftCount = models.filter((model) => model.draft).length;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-1 text-sm text-muted">模型目录</p>
          <h1 className="text-2xl font-semibold tracking-tight">模型与价格版本</h1>
          <p className="mt-2 max-w-2xl text-sm text-muted">
            先保存草稿，再显式发布。线上请求始终读取不可变的已发布版本。
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onPress={() => window.location.assign("/admin/providers")} variant="secondary">
            <Server size={16} /> 供应商设置
          </Button>
          <Button isDisabled={loading} onPress={() => void load()} variant="secondary">
            <RefreshCw size={16} /> 刷新
          </Button>
          <Button onPress={beginCreate} variant="primary">
            <Plus size={16} /> 新增模型
          </Button>
        </div>
      </header>

      {(error || notice) && (
        <div
          className={`rounded-xl px-4 py-3 text-sm ${error ? "bg-danger-soft text-danger" : "bg-success-soft text-success"}`}
          role={error ? "alert" : "status"}
        >
          {error ?? notice}
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-3">
        <Summary icon={Boxes} label="模型总数" value={models.length} />
        <Summary icon={CloudUpload} label="线上可用" value={publishedCount} />
        <Summary icon={PencilLine} label="待发布草稿" value={draftCount} />
      </section>

      {activeProviders.length === 0 && !loading && (
        <div className="rounded-xl bg-warning-soft px-4 py-3 text-sm text-warning">
          创建模型前需要至少一个已启用的供应商。请先进入“供应商设置”保存连接和 API Key。
        </div>
      )}

      <section className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(390px,.85fr)]">
        <div className="space-y-4">
          {loading ? (
            <Card variant="default">
              <Card.Content className="py-10 text-center text-sm text-muted">正在读取模型目录…</Card.Content>
            </Card>
          ) : models.length === 0 ? (
            <Card variant="default">
              <Card.Content className="items-center py-12 text-center">
                <Boxes className="text-muted" size={26} />
                <p className="mt-3 text-sm font-medium">还没有模型</p>
                <p className="mt-1 text-xs text-muted">从右侧创建第一个模型草稿，确认后再发布。</p>
              </Card.Content>
            </Card>
          ) : models.map((model) => (
            <ModelCard
              history={versions[model.modelId]}
              historyOpen={historyModelId === model.modelId}
              key={model.modelId}
              model={model}
              pending={pending}
              providers={providers}
              onEdit={() => setSelectedModelId(model.modelId)}
              onDiscard={() => void discardDraft(model)}
              onHistory={() => void toggleHistory(model)}
              onPublish={() => void publish(model)}
              onStartDraft={() => void startDraft(model)}
              onToggleStatus={() => void toggleStatus(model)}
            />
          ))}
        </div>

        <ModelEditor
          key={selectedModel
            ? `${selectedModel.modelId}:${selectedModel.draft?.id}:${selectedModel.draft?.revision}`
            : `new:${newFormVersion}`}
          model={selectedModel}
          pending={pending !== null}
          providers={editorProviders}
          onCancel={selectedModel ? beginCreate : undefined}
          onDiscard={selectedModel?.draft ? () => void discardDraft(selectedModel) : undefined}
          onSubmit={save}
        />
      </section>
    </div>
  );
}

function ModelCard({
  model,
  providers,
  history,
  historyOpen,
  pending,
  onEdit,
  onDiscard,
  onStartDraft,
  onPublish,
  onToggleStatus,
  onHistory,
}: {
  model: AdminModel;
  providers: ModelProvider[];
  history?: ModelVersion[];
  historyOpen: boolean;
  pending: string | null;
  onEdit: () => void;
  onDiscard: () => void;
  onStartDraft: () => void;
  onPublish: () => void;
  onToggleStatus: () => void;
  onHistory: () => void;
}) {
  const current = model.draft ?? model.published;
  const provider = providerFor(providers, current?.providerId);
  const isBusy = pending?.endsWith(`:${model.modelId}`) ?? false;

  return (
    <Card variant="default">
      <Card.Header className="flex-row items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <Card.Title>{current?.displayName ?? model.code}</Card.Title>
            <Chip color={model.status === "ACTIVE" ? "success" : "default"} size="sm" variant="soft">
              {model.status === "ACTIVE" ? "已启用" : "已停用"}
            </Chip>
            {model.draft && <Chip color="warning" size="sm" variant="soft">草稿 v{model.draft.versionNo}</Chip>}
            {model.published && <Chip color="accent" size="sm" variant="soft">线上 v{model.published.versionNo}</Chip>}
          </div>
          <Card.Description className="mt-1">{model.code} · {provider?.name ?? "未知供应商"}</Card.Description>
        </div>
        <Button isDisabled={isBusy} onPress={onToggleStatus} size="sm" variant="tertiary">
          <Power size={15} /> {model.status === "ACTIVE" ? "停用" : "启用"}
        </Button>
      </Card.Header>
      <Card.Content className="gap-4 pt-1">
        {current && (
          <div className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
            <Info label="上游模型" value={current.upstreamModel} mono />
            <Info label="API 格式" value={protocolLabel(current.protocolType)} />
            <Info label="上下文窗口" value={formatInteger(current.capabilities.contextWindow)} />
            <Info label="最大输出" value={formatInteger(current.capabilities.maxOutputTokens)} />
          </div>
        )}

        <div className="flex flex-wrap gap-2">
          {model.draft ? (
            <Button isDisabled={isBusy} onPress={onEdit} size="sm" variant="secondary">
              <PencilLine size={15} /> 编辑草稿
            </Button>
          ) : model.published ? (
            <Button isDisabled={isBusy} onPress={onStartDraft} size="sm" variant="secondary">
              <CopyPlus size={15} /> 从线上版本创建草稿
            </Button>
          ) : null}
          {model.draft && (
            <Button
              isDisabled={isBusy || model.status !== "ACTIVE"}
              onPress={onPublish}
              size="sm"
              variant="primary"
            >
              <CloudUpload size={15} /> 发布 v{model.draft.versionNo}
            </Button>
          )}
          {model.draft && (
            <Button isDisabled={isBusy} onPress={onDiscard} size="sm" variant="danger-soft">
              <Trash2 size={15} /> 放弃草稿
            </Button>
          )}
          <Button isDisabled={isBusy} onPress={onHistory} size="sm" variant="tertiary">
            <History size={15} /> {historyOpen ? "收起版本" : "版本记录"}
          </Button>
        </div>

        {historyOpen && (
          <VersionHistory
            loading={pending === `history:${model.modelId}`}
            modelCode={model.code}
            providers={providers}
            versions={history}
          />
        )}
      </Card.Content>
    </Card>
  );
}

function ModelEditor({
  model,
  providers,
  pending,
  onSubmit,
  onCancel,
  onDiscard,
}: {
  model: AdminModel | null;
  providers: ModelProvider[];
  pending: boolean;
  onSubmit: (submission: ModelEditorSubmission) => Promise<void>;
  onCancel?: () => void;
  onDiscard?: () => void;
}) {
  const draft = model?.draft;
  const initialVersion = draft ? versionDefaults(draft) : emptyVersion();
  const costPolicyDefaults = costTimePricingPolicyDefaults(initialVersion);
  const quotaPolicyDefaults = quotaTimePricingPolicyDefaults(initialVersion);
  const canEdit = !model || Boolean(draft);
  const [code, setCode] = useState(model?.code ?? "");
  const [providerId, setProviderId] = useState(String(
    draft?.providerId ?? providers.find((provider) => provider.status === "ACTIVE")?.id ?? providers[0]?.id ?? "",
  ));
  const [version, setVersion] = useState<ModelVersionInput>(initialVersion);
  const [dirty, setDirty] = useState(false);
  const [costCacheCreationEnabled, setCostCacheCreationEnabled] = useState(
    (initialVersion.cacheCreationInputCostPerMillion ?? 0) > 0,
  );
  const [quotaCacheCreationEnabled, setQuotaCacheCreationEnabled] = useState(
    (initialVersion.cacheCreationInputQuotaPerMillion ?? 0) > 0,
  );
  const [costTimePricingEnabled, setCostTimePricingEnabled] = useState(Boolean(initialVersion.costTimePricingPolicy));
  const [costTimePricingZone, setCostTimePricingZone] = useState(costPolicyDefaults.zoneId);
  const [costTimeRules, setCostTimeRules] = useState<EditableCostTimePricingRule[]>(
    costPolicyDefaults.rules.map((rule) => editableCostTimeRule(rule, defaultCostRates(initialVersion))),
  );
  const [quotaTimePricingEnabled, setQuotaTimePricingEnabled] = useState(Boolean(initialVersion.quotaTimePricingPolicy));
  const [quotaTimePricingZone, setQuotaTimePricingZone] = useState(quotaPolicyDefaults.zoneId);
  const [defaultQuotaMultiplier, setDefaultQuotaMultiplier] = useState(quotaPolicyDefaults.defaultQuotaMultiplier);
  const [quotaTimeRules, setQuotaTimeRules] = useState<EditableQuotaTimePricingRule[]>(
    quotaPolicyDefaults.rules.map((rule) => editableQuotaTimeRule(rule)),
  );

  function updateVersion(update: Partial<ModelVersionInput>) {
    setVersion((current) => ({ ...current, ...update }));
    setDirty(true);
  }

  function addCostTimeRule() {
    setCostTimeRules((current) => [
      ...current,
      editableCostTimeRule(undefined, defaultCostRates(version), current.length + 1),
    ]);
    setDirty(true);
  }

  function updateCostTimeRule(id: string, update: Partial<EditableCostTimePricingRule>) {
    setCostTimeRules((current) => current.map((rule) => rule.id === id ? { ...rule, ...update } : rule));
    setDirty(true);
  }

  function removeCostTimeRule(id: string) {
    setCostTimeRules((current) => current.filter((rule) => rule.id !== id));
    setDirty(true);
  }

  function addQuotaTimeRule() {
    setQuotaTimeRules((current) => [
      ...current,
      editableQuotaTimeRule(undefined, current.length + 1),
    ]);
    setDirty(true);
  }

  function updateQuotaTimeRule(id: string, update: Partial<EditableQuotaTimePricingRule>) {
    setQuotaTimeRules((current) => current.map((rule) => rule.id === id ? { ...rule, ...update } : rule));
    setDirty(true);
  }

  function removeQuotaTimeRule(id: string) {
    setQuotaTimeRules((current) => current.filter((rule) => rule.id !== id));
    setDirty(true);
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const submittedVersion: ModelVersionInput = {
      ...version,
      cacheCreationInputCostPerMillion: costCacheCreationEnabled
        ? version.cacheCreationInputCostPerMillion ?? 0
        : undefined,
      costTimePricingPolicy: costTimePricingEnabled ? {
        zoneId: costTimePricingZone.trim(),
        rules: costTimeRules.map(costTimeRuleInput),
      } : undefined,
      cacheCreationInputQuotaPerMillion: quotaCacheCreationEnabled
        ? version.cacheCreationInputQuotaPerMillion ?? 0
        : undefined,
      quotaTimePricingPolicy: quotaTimePricingEnabled ? {
        zoneId: quotaTimePricingZone.trim(),
        defaultQuotaMultiplier,
        rules: quotaTimeRules.map(quotaTimeRuleInput),
      } : undefined,
    };
    void onSubmit({ code: code.trim(), providerId: Number(providerId), version: submittedVersion });
  }

  return (
    <Card className="xl:sticky xl:top-24" variant="default">
      <Card.Header>
        <span className="grid size-9 place-items-center rounded-xl bg-default text-muted">
          {model ? <PencilLine size={18} /> : <Plus size={18} />}
        </span>
        <div>
          <Card.Title>{model ? "编辑模型草稿" : "新增模型"}</Card.Title>
          <Card.Description>
            {model
              ? `${model.code} · 草稿 v${draft?.versionNo ?? "-"} · ${dirty ? "有未保存修改" : "已保存"}`
              : "创建后先进入草稿状态"}
          </Card.Description>
        </div>
      </Card.Header>
      <Card.Content>
        {!canEdit ? (
          <div className="rounded-xl bg-default/50 p-4 text-sm text-muted">
            当前模型没有草稿，请先在左侧点击“从线上版本创建草稿”。
          </div>
        ) : (
          <form className="space-y-6" onSubmit={submit}>
            <EditorSection icon={SlidersHorizontal} title="基础信息">
              {model ? (
                <TextField fullWidth>
                  <Label>模型编码</Label>
                  <Input fullWidth readOnly value={code} />
                  <p className="mt-1 text-xs text-muted">模型编码创建后保持不变，用于稳定的网关调用标识。</p>
                </TextField>
              ) : (
                <TextField fullWidth isRequired name="code">
                  <Label>模型编码</Label>
                  <Input
                    fullWidth
                    onChange={(event) => { setCode(event.target.value); setDirty(true); }}
                    placeholder="例如 gpt-4o-mini"
                    value={code}
                  />
                </TextField>
              )}
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField fullWidth isRequired name="displayName">
                  <Label>显示名称</Label>
                  <Input
                    fullWidth
                    onChange={(event) => updateVersion({ displayName: event.target.value })}
                    placeholder="例如 GPT-4o mini"
                    value={version.displayName}
                  />
                </TextField>
                <TextField fullWidth isRequired name="upstreamModel">
                  <Label>上游模型 ID</Label>
                  <Input
                    fullWidth
                    onChange={(event) => updateVersion({ upstreamModel: event.target.value })}
                    placeholder="例如 gpt-4o-mini"
                    value={version.upstreamModel}
                  />
                </TextField>
              </div>
              <TextField fullWidth name="description">
                <Label>模型说明</Label>
                <TextArea
                  fullWidth
                  onChange={(event) => updateVersion({ description: event.target.value })}
                  placeholder="面向用户展示的简短说明"
                  rows={2}
                  value={version.description}
                />
              </TextField>
              <Select
                fullWidth
                isRequired
                name="providerId"
                onSelectionChange={(key) => {
                  if (!key) return;
                  setProviderId(String(key));
                  setDirty(true);
                }}
                placeholder="请选择供应商"
                selectedKey={providerId}
                variant="secondary"
              >
                <Label>模型供应商</Label>
                <Select.Trigger>
                  <Select.Value>{({ selectedText }) => selectedText}</Select.Value>
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {providers.map((provider) => (
                      <ListBox.Item id={String(provider.id)} key={provider.id} textValue={provider.name}>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-medium">{provider.name}</p>
                          <p className="truncate text-xs text-muted">
                            {protocolLabel(provider.protocolType)} · {provider.status === "ACTIVE" ? "已启用" : "已停用"} · {provider.baseUrl}
                          </p>
                        </div>
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>
              <div className="grid gap-4 sm:grid-cols-2">
                <NumericField
                  label="上下文窗口"
                  name="contextWindow"
                  onChange={(value) => updateVersion({ contextWindow: value })}
                  step="1"
                  value={version.contextWindow}
                />
                <NumericField
                  label="最大输出 Token"
                  name="maxOutputTokens"
                  onChange={(value) => updateVersion({ maxOutputTokens: value })}
                  step="1"
                  value={version.maxOutputTokens}
                />
              </div>
            </EditorSection>

            <EditorSection icon={CheckCircle2} title="模型能力">
              <div className="grid gap-3 sm:grid-cols-2">
                <Capability isSelected={version.supportsReasoning} label="推理 / Thinking" name="supportsReasoning" onChange={(selected) => updateVersion({ supportsReasoning: selected })} />
                <Capability isSelected={version.supportsTools} label="工具调用" name="supportsTools" onChange={(selected) => updateVersion({ supportsTools: selected })} />
                <Capability isSelected={version.supportsVision} label="图片输入" name="supportsVision" onChange={(selected) => updateVersion({ supportsVision: selected })} />
                <Capability isSelected={version.supportsJson} label="JSON 输出" name="supportsJson" onChange={(selected) => updateVersion({ supportsJson: selected })} />
              </div>
            </EditorSection>

            <EditorSection icon={CircleDollarSign} title="默认上游成本">
              <div className="grid gap-4 sm:grid-cols-2">
                <Select
                  fullWidth
                  isRequired
                  name="costCurrency"
                  onSelectionChange={(key) => key && updateVersion({ costCurrency: String(key) })}
                  selectedKey={version.costCurrency}
                  variant="secondary"
                >
                  <Label>成本币种</Label>
                  <Select.Trigger><Select.Value /><Select.Indicator /></Select.Trigger>
                  <Select.Popover>
                    <ListBox>
                      <ListBox.Item id="USD" textValue="USD">USD</ListBox.Item>
                      <ListBox.Item id="CNY" textValue="CNY">CNY</ListBox.Item>
                    </ListBox>
                  </Select.Popover>
                </Select>
              </div>
              <p className="text-xs leading-5 text-muted">
                未命中时段成本覆盖规则时使用这里的价格。金额单位由成本币种决定，当前为
                <span className="font-medium text-foreground"> {version.costCurrency} / 百万 Token</span>；切换币种不会自动换算已填数值，
                推理 Token 统一按输出价格计算。
              </p>
              <div className="grid gap-4 sm:grid-cols-2">
                {costFields.map((field) => (
                  <NumericField
                    key={field.name}
                    label={`${field.label}成本（${version.costCurrency} / 百万 Token）`}
                    name={field.name}
                    onChange={(value) => updateVersion({ [field.name]: value } as Partial<ModelVersionInput>)}
                    value={version[field.name]}
                  />
                ))}
                {costCacheCreationEnabled && (
                  <NumericField
                    label={`缓存创建输入成本（${version.costCurrency} / 百万 Token）`}
                    name="cacheCreationInputCostPerMillion"
                    onChange={(value) => updateVersion({ cacheCreationInputCostPerMillion: value })}
                    value={version.cacheCreationInputCostPerMillion ?? 0}
                  />
                )}
              </div>
              <OptionalPricingToggle
                description="仅在供应商明确返回并计费 cache creation usage 时开启。"
                isSelected={costCacheCreationEnabled}
                label="配置缓存创建输入成本"
                name="costCacheCreationEnabled"
                onChange={(selected) => { setCostCacheCreationEnabled(selected); setDirty(true); }}
              />
              <div className="rounded-xl border border-border bg-default/30 p-4">
                <OptionalPricingToggle
                  description="供应商在不同星期或时间段采用不同 Token 成本时开启；不会影响用户套餐 Credits。"
                  isSelected={costTimePricingEnabled}
                  label="启用上游成本时段规则"
                  name="costTimePricingEnabled"
                  onChange={(selected) => { setCostTimePricingEnabled(selected); setDirty(true); }}
                />
                {costTimePricingEnabled && (
                  <div className="mt-5 space-y-5 border-t border-separator pt-5">
                    <TextField fullWidth isRequired name="costTimePricingZone">
                      <Label>上游成本计价时区</Label>
                      <Input
                        fullWidth
                        onChange={(event) => { setCostTimePricingZone(event.target.value); setDirty(true); }}
                        placeholder="Asia/Shanghai"
                        value={costTimePricingZone}
                      />
                    </TextField>
                    <TimeRuleHint />
                    <div className="space-y-4">
                      {costTimeRules.map((rule, index) => (
                        <CostTimePricingRuleEditor
                          costCurrency={version.costCurrency}
                          index={index}
                          key={rule.id}
                          onChange={(update) => updateCostTimeRule(rule.id, update)}
                          onRemove={() => removeCostTimeRule(rule.id)}
                          rule={rule}
                        />
                      ))}
                    </div>
                    <Button onPress={addCostTimeRule} size="sm" variant="secondary">
                      <Plus size={15} /> 添加上游成本时段
                    </Button>
                    {costTimeRules.length === 0 && (
                      <p className="text-xs text-danger">启用上游成本时段后至少需要添加一条规则。</p>
                    )}
                  </div>
                )}
              </div>
            </EditorSection>

            <EditorSection icon={Archive} title="默认套餐额度费率">
              <p className="text-xs leading-5 text-muted">
                这里不是金额，而是用户套餐扣减额度，单位固定为 Credits。未命中时段规则时按这些费率和默认倍率计算；
                1 Credit 的名义价值为 ¥0.05。至少一个费率或最低请求额度必须大于 0。
              </p>
              <div className="grid gap-4 sm:grid-cols-2">
                {quotaFields.map((field) => (
                  <NumericField
                    key={field.name}
                    label={`${field.label}额度（Credits / 百万 Token）`}
                    name={field.name}
                    onChange={(value) => updateVersion({ [field.name]: value } as Partial<ModelVersionInput>)}
                    value={version[field.name]}
                  />
                ))}
                {quotaCacheCreationEnabled && (
                  <NumericField
                    label="缓存创建输入额度（Credits / 百万 Token）"
                    name="cacheCreationInputQuotaPerMillion"
                    onChange={(value) => updateVersion({ cacheCreationInputQuotaPerMillion: value })}
                    value={version.cacheCreationInputQuotaPerMillion ?? 0}
                  />
                )}
                <NumericField
                  label="单次最低额度（Credits）"
                  name="minimumRequestQuota"
                  onChange={(value) => updateVersion({ minimumRequestQuota: value })}
                  value={version.minimumRequestQuota}
                />
              </div>
              <OptionalPricingToggle
                description="未开启时，缓存创建用量不会单独扣除套餐额度。"
                isSelected={quotaCacheCreationEnabled}
                label="缓存创建输入单独扣减额度"
                name="quotaCacheCreationEnabled"
                onChange={(selected) => { setQuotaCacheCreationEnabled(selected); setDirty(true); }}
              />
              <div className="rounded-xl border border-border bg-default/30 p-4">
                <OptionalPricingToggle
                  description="面向用户的套餐在不同时段采用不同额度倍率时开启；不会改变供应商成本。"
                  isSelected={quotaTimePricingEnabled}
                  label="启用套餐额度时段规则"
                  name="quotaTimePricingEnabled"
                  onChange={(selected) => { setQuotaTimePricingEnabled(selected); setDirty(true); }}
                />
                {quotaTimePricingEnabled && (
                  <div className="mt-5 space-y-5 border-t border-separator pt-5">
                    <div className="grid gap-4 sm:grid-cols-2">
                      <TextField fullWidth isRequired name="quotaTimePricingZone">
                        <Label>套餐额度计价时区</Label>
                        <Input
                          fullWidth
                          onChange={(event) => { setQuotaTimePricingZone(event.target.value); setDirty(true); }}
                          placeholder="Asia/Shanghai"
                          value={quotaTimePricingZone}
                        />
                      </TextField>
                      <NumericField
                        label="未命中规则的额度倍率"
                        min="0.000001"
                        name="defaultQuotaMultiplier"
                        onChange={(value) => { setDefaultQuotaMultiplier(value); setDirty(true); }}
                        value={defaultQuotaMultiplier}
                      />
                    </div>
                    <TimeRuleHint />
                    <div className="space-y-4">
                      {quotaTimeRules.map((rule, index) => (
                        <QuotaTimePricingRuleEditor
                          index={index}
                          key={rule.id}
                          onChange={(update) => updateQuotaTimeRule(rule.id, update)}
                          onRemove={() => removeQuotaTimeRule(rule.id)}
                          rule={rule}
                        />
                      ))}
                    </div>
                    <Button onPress={addQuotaTimeRule} size="sm" variant="secondary">
                      <Plus size={15} /> 添加套餐额度时段
                    </Button>
                    {quotaTimeRules.length === 0 && (
                      <p className="text-xs text-danger">启用套餐额度时段后至少需要添加一条规则。</p>
                    )}
                  </div>
                )}
              </div>
            </EditorSection>

            <div className="flex justify-end gap-2">
              {onDiscard && (
                <Button isDisabled={pending} onPress={onDiscard} variant="danger-soft">
                  <Trash2 size={15} /> 放弃草稿
                </Button>
              )}
              {onCancel && <Button onPress={onCancel} variant="tertiary">关闭编辑</Button>}
              <Button
                fullWidth={!onCancel}
                isDisabled={pending || providers.length === 0
                  || (Boolean(model) && !dirty)
                  || (costTimePricingEnabled && costTimeRules.length === 0)
                  || (quotaTimePricingEnabled && quotaTimeRules.length === 0)}
                type="submit"
                variant="primary"
              >
                {pending ? "正在保存…" : model ? dirty ? "保存草稿" : "草稿已保存" : "创建模型草稿"}
              </Button>
            </div>
          </form>
        )}
      </Card.Content>
    </Card>
  );
}

function EditorSection({ icon: Icon, title, children }: {
  icon: typeof Boxes;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <fieldset className="space-y-4 border-0 p-0">
      <legend className="mb-4 flex items-center gap-2 text-sm font-medium">
        <Icon className="text-muted" size={16} /> {title}
      </legend>
      {children}
    </fieldset>
  );
}

function Capability({ name, label, isSelected, onChange }: {
  name: string;
  label: string;
  isSelected: boolean;
  onChange: (selected: boolean) => void;
}) {
  return (
    <Checkbox isSelected={isSelected} name={name} onChange={onChange} value="true">
      <Checkbox.Content>
        <Checkbox.Control><Checkbox.Indicator /></Checkbox.Control>
        <Label>{label}</Label>
      </Checkbox.Content>
    </Checkbox>
  );
}

function OptionalPricingToggle({
  name,
  label,
  description,
  isSelected,
  onChange,
}: {
  name: string;
  label: string;
  description: string;
  isSelected: boolean;
  onChange: (selected: boolean) => void;
}) {
  return (
    <Checkbox isSelected={isSelected} name={name} onChange={onChange} value="true">
      <Checkbox.Content>
        <Checkbox.Control><Checkbox.Indicator /></Checkbox.Control>
        <div>
          <Label>{label}</Label>
          <p className="mt-1 text-xs leading-5 text-muted">{description}</p>
        </div>
      </Checkbox.Content>
    </Checkbox>
  );
}

function CostTimePricingRuleEditor({ rule, index, costCurrency, onChange, onRemove }: {
  rule: EditableCostTimePricingRule;
  index: number;
  costCurrency: string;
  onChange: (update: Partial<EditableCostTimePricingRule>) => void;
  onRemove: () => void;
}) {
  function updateRate(key: keyof ModelRates, value: number) {
    onChange({ costRates: { ...rule.costRates, [key]: value } });
  }

  function toggleDay(day: PricingDay, selected: boolean) {
    const days = selected
      ? [...rule.daysOfWeek, day]
      : rule.daysOfWeek.filter((current) => current !== day);
    onChange({ daysOfWeek: pricingDays.map(({ key }) => key).filter((key) => days.includes(key)) });
  }

  return (
    <div className="space-y-4 rounded-xl border border-border bg-background/40 p-4">
      <div className="flex items-start gap-3">
        <TextField className="flex-1" fullWidth isRequired>
          <Label>规则名称</Label>
          <Input
            fullWidth
            maxLength={80}
            onChange={(event) => onChange({ name: event.target.value })}
            placeholder={`例如 工作日峰时 ${index + 1}`}
            value={rule.name}
          />
        </TextField>
        <Button aria-label="删除时段规则" className="mt-6" isIconOnly onPress={onRemove} size="sm" variant="tertiary">
          <Trash2 size={15} />
        </Button>
      </div>

      <div>
        <p className="mb-2 text-xs text-muted">生效星期</p>
        <div className="flex flex-wrap gap-x-4 gap-y-2">
          {pricingDays.map((day) => (
            <Checkbox
              isSelected={rule.daysOfWeek.includes(day.key)}
              key={day.key}
              onChange={(selected) => toggleDay(day.key, selected)}
            >
              <Checkbox.Content>
                <Checkbox.Control><Checkbox.Indicator /></Checkbox.Control>
                <Label>{day.label}</Label>
              </Checkbox.Content>
            </Checkbox>
          ))}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <ControlledField
          label="开始时间"
          onChange={(value) => onChange({ startTime: value })}
          type="time"
          value={rule.startTime.slice(0, 5)}
        />
        <ControlledField
          label="结束时间"
          onChange={(value) => onChange({ endTime: value })}
          type="time"
          value={rule.endTime.slice(0, 5)}
        />
      </div>

      <div className="space-y-4 border-t border-separator pt-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <ControlledField
            label={`未缓存输入成本（${costCurrency} / 百万 Token）`}
            min="0"
            onChange={(value) => updateRate("uncachedInputPerMillion", Number(value))}
            step="0.000001"
            type="number"
            value={String(rule.costRates.uncachedInputPerMillion)}
          />
          <ControlledField
            label={`缓存命中输入成本（${costCurrency} / 百万 Token）`}
            min="0"
            onChange={(value) => updateRate("cachedInputPerMillion", Number(value))}
            step="0.000001"
            type="number"
            value={String(rule.costRates.cachedInputPerMillion)}
          />
          <ControlledField
            label={`输出成本（含推理，${costCurrency} / 百万 Token）`}
            min="0"
            onChange={(value) => updateRate("outputPerMillion", Number(value))}
            step="0.000001"
            type="number"
            value={String(rule.costRates.outputPerMillion)}
          />
          {rule.cacheCreationEnabled && (
            <ControlledField
              label={`缓存创建输入成本（${costCurrency} / 百万 Token）`}
              min="0"
              onChange={(value) => updateRate("cacheCreationInputPerMillion", Number(value))}
              step="0.000001"
              type="number"
              value={String(rule.costRates.cacheCreationInputPerMillion)}
            />
          )}
        </div>
        <OptionalPricingToggle
          description="仅在该供应商对此时段的 cache creation usage 单独计费时开启。"
          isSelected={rule.cacheCreationEnabled}
          label="该时段包含缓存创建输入成本"
          name={`costCacheCreation:${rule.id}`}
          onChange={(selected) => onChange({ cacheCreationEnabled: selected })}
        />
      </div>
    </div>
  );
}

function QuotaTimePricingRuleEditor({ rule, index, onChange, onRemove }: {
  rule: EditableQuotaTimePricingRule;
  index: number;
  onChange: (update: Partial<EditableQuotaTimePricingRule>) => void;
  onRemove: () => void;
}) {
  function toggleDay(day: PricingDay, selected: boolean) {
    const days = selected
      ? [...rule.daysOfWeek, day]
      : rule.daysOfWeek.filter((current) => current !== day);
    onChange({ daysOfWeek: pricingDays.map(({ key }) => key).filter((key) => days.includes(key)) });
  }

  return (
    <div className="space-y-4 rounded-xl border border-border bg-background/40 p-4">
      <div className="flex items-start gap-3">
        <TextField className="flex-1" fullWidth isRequired>
          <Label>规则名称</Label>
          <Input
            fullWidth
            maxLength={80}
            onChange={(event) => onChange({ name: event.target.value })}
            placeholder={`例如 工作日峰时 ${index + 1}`}
            value={rule.name}
          />
        </TextField>
        <Button aria-label="删除套餐额度时段" className="mt-6" isIconOnly onPress={onRemove} size="sm" variant="tertiary">
          <Trash2 size={15} />
        </Button>
      </div>
      <div>
        <p className="mb-2 text-xs text-muted">生效星期</p>
        <div className="flex flex-wrap gap-x-4 gap-y-2">
          {pricingDays.map((day) => (
            <Checkbox
              isSelected={rule.daysOfWeek.includes(day.key)}
              key={day.key}
              onChange={(selected) => toggleDay(day.key, selected)}
            >
              <Checkbox.Content>
                <Checkbox.Control><Checkbox.Indicator /></Checkbox.Control>
                <Label>{day.label}</Label>
              </Checkbox.Content>
            </Checkbox>
          ))}
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <ControlledField
          label="开始时间"
          onChange={(value) => onChange({ startTime: value })}
          type="time"
          value={rule.startTime.slice(0, 5)}
        />
        <ControlledField
          label="结束时间"
          onChange={(value) => onChange({ endTime: value })}
          type="time"
          value={rule.endTime.slice(0, 5)}
        />
        <ControlledField
          label="套餐额度倍率"
          min="0.000001"
          onChange={(value) => onChange({ quotaMultiplier: Number(value) })}
          step="0.000001"
          type="number"
          value={String(rule.quotaMultiplier)}
        />
      </div>
    </div>
  );
}

function TimeRuleHint() {
  return (
    <div className="flex items-start gap-2 rounded-xl bg-default/50 px-3 py-2.5 text-xs leading-5 text-muted">
      <Clock3 className="mt-0.5 shrink-0" size={15} />
      星期表示规则的开始日；22:00–06:00 会延续到次日。多条规则可以选择相同星期，只要当天的时间段
      不重叠即可；结束时间不包含在当前规则内。
    </div>
  );
}

function ControlledField({ label, value, type, onChange, min, step }: {
  label: string;
  value: string;
  type: "number" | "time";
  onChange: (value: string) => void;
  min?: string;
  step?: string;
}) {
  return (
    <TextField fullWidth isRequired>
      <Label>{label}</Label>
      <Input
        fullWidth
        min={min}
        onChange={(event) => onChange(event.target.value)}
        step={step}
        type={type}
        value={value}
      />
    </TextField>
  );
}

function NumericField({ name, label, value, onChange, step = "0.000001", min = "0" }: {
  name: string;
  label: string;
  value: number;
  onChange: (value: number) => void;
  step?: string;
  min?: string;
}) {
  return (
    <TextField fullWidth isRequired name={name}>
      <Label>{label}</Label>
      <Input
        fullWidth
        min={min}
        onChange={(event) => onChange(Number(event.target.value))}
        step={step}
        type="number"
        value={String(value)}
      />
    </TextField>
  );
}

function VersionHistory({ versions, loading, modelCode, providers }: {
  versions?: ModelVersion[];
  loading: boolean;
  modelCode: string;
  providers: ModelProvider[];
}) {
  return (
    <div className="rounded-xl border border-border bg-default/30 p-4">
      <p className="mb-3 text-sm font-medium">版本记录</p>
      {loading || !versions ? (
        <p className="text-xs text-muted">正在读取版本…</p>
      ) : versions.length === 0 ? (
        <p className="text-xs text-muted">暂无版本。</p>
      ) : (
        <div className="space-y-3">
          {versions.map((version) => (
            <details className="group rounded-xl border border-border bg-background/60" key={version.id}>
              <summary className="flex cursor-pointer list-none items-center gap-3 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium">v{version.versionNo} · {version.displayName}</span>
                    <VersionStatus status={version.status} />
                  </div>
                  <p className="mt-1 truncate text-xs text-muted">
                    {version.upstreamModel} · {providerFor(providers, version.providerId)?.name ?? `供应商 #${version.providerId}`} · {formatDate(version.publishedAt ?? version.createdAt)}
                  </p>
                </div>
                <span className="hidden font-mono text-xs text-muted sm:inline" title={version.pricingVersion}>
                  {version.pricingVersion.slice(0, 8)}
                </span>
                <ChevronDown className="shrink-0 text-muted transition-transform group-open:rotate-180" size={16} />
              </summary>
              <VersionDetails modelCode={modelCode} provider={providerFor(providers, version.providerId)} version={version} />
            </details>
          ))}
        </div>
      )}
    </div>
  );
}

function VersionDetails({ version, modelCode, provider }: {
  version: ModelVersion;
  modelCode: string;
  provider?: ModelProvider;
}) {
  return (
    <div className="space-y-5 border-t border-separator px-4 py-4">
      <VersionDetailSection title="基础信息">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <VersionInfo label="模型编码" mono value={modelCode} />
          <VersionInfo label="显示名称" value={version.displayName} />
          <VersionInfo label="上游模型 ID" mono value={version.upstreamModel} />
          <VersionInfo label="供应商" value={provider?.name ?? `供应商 #${version.providerId}`} />
          <VersionInfo label="API 格式" value={protocolLabel(version.protocolType)} />
          <VersionInfo label="API Base URL" mono value={version.baseUrl} />
          <VersionInfo label="上下文窗口" value={formatInteger(version.capabilities.contextWindow)} />
          <VersionInfo label="最大输出 Token" value={formatInteger(version.capabilities.maxOutputTokens)} />
          <VersionInfo label="模型能力" value={capabilitySummary(version)} />
        </div>
        <VersionInfo label="模型说明" value={version.description || "未填写"} />
      </VersionDetailSection>

      <VersionDetailSection title="默认上游成本">
        <p className="text-xs leading-5 text-muted">
          金额单位：{version.costCurrency} / 百万 Token；未命中时段覆盖规则时使用以下默认成本。
        </p>
        <RateGrid
          cacheCreation={version.costRates.cacheCreationInputPerMillion}
          cached={version.costRates.cachedInputPerMillion}
          output={version.costRates.outputPerMillion}
          suffix={`${version.costCurrency} / 百万 Token`}
          uncached={version.costRates.uncachedInputPerMillion}
        />
        <CostTimePolicySnapshot currency={version.costCurrency} policy={version.costTimePricingPolicy} />
      </VersionDetailSection>

      <VersionDetailSection title="默认套餐额度费率">
        <p className="text-xs leading-5 text-muted">
          单位：Credits / 百万 Token；推理 Token 计入输出，未命中额度时段规则时使用以下默认费率。
        </p>
        <RateGrid
          cacheCreation={version.quotaRates.cacheCreationInputPerMillion}
          cached={version.quotaRates.cachedInputPerMillion}
          output={version.quotaRates.outputPerMillion}
          suffix="Credits / 百万 Token"
          uncached={version.quotaRates.uncachedInputPerMillion}
        />
        <VersionInfo label="单次最低额度" value={`${formatDecimal(version.quotaRates.minimumRequestQuota)} Credits`} />
        <QuotaTimePolicySnapshot policy={version.quotaTimePricingPolicy} />
      </VersionDetailSection>

      <VersionDetailSection title="版本元数据">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <VersionInfo label="版本 ID" mono value={version.id} />
          <VersionInfo label="计价版本" mono value={version.pricingVersion} />
          <VersionInfo label="数据修订号" value={String(version.revision)} />
          <VersionInfo label="创建时间" value={formatDate(version.createdAt)} />
          <VersionInfo label="更新时间" value={formatDate(version.updatedAt)} />
          <VersionInfo label="发布时间" value={version.publishedAt ? formatDate(version.publishedAt) : "尚未发布"} />
        </div>
      </VersionDetailSection>
    </div>
  );
}

function VersionDetailSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-3">
      <h4 className="text-xs font-medium text-foreground">{title}</h4>
      {children}
    </section>
  );
}

function VersionInfo({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="min-w-0 rounded-lg bg-default/40 px-3 py-2.5">
      <p className="text-xs text-muted">{label}</p>
      <p className={`mt-1 break-words text-sm ${mono ? "font-mono text-xs" : ""}`}>{value}</p>
    </div>
  );
}

function RateGrid({ uncached, cached, cacheCreation, output, suffix }: {
  uncached: number;
  cached: number;
  cacheCreation: number;
  output: number;
  suffix: string;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <VersionInfo label="未缓存输入" value={`${formatDecimal(uncached)} ${suffix}`} />
      <VersionInfo label="缓存命中输入" value={`${formatDecimal(cached)} ${suffix}`} />
      <VersionInfo
        label="缓存创建输入"
        value={cacheCreation > 0 ? `${formatDecimal(cacheCreation)} ${suffix}` : "未单独配置"}
      />
      <VersionInfo label="输出（含推理）" value={`${formatDecimal(output)} ${suffix}`} />
    </div>
  );
}

function CostTimePolicySnapshot({ policy, currency }: {
  policy?: CostTimePricingPolicy | null;
  currency: string;
}) {
  if (!policy) {
    return <p className="rounded-lg bg-default/40 px-3 py-2.5 text-xs text-muted">上游成本时段规则未启用。</p>;
  }
  return (
    <div className="space-y-3 rounded-lg border border-border p-3">
      <p className="text-xs font-medium">上游成本时段规则 · {policy.zoneId}</p>
      {policy.rules.map((rule, index) => (
        <div className="space-y-3 border-t border-separator pt-3 first:border-0 first:pt-0" key={`${rule.name}:${index}`}>
          <p className="text-sm font-medium">{rule.name}</p>
          <p className="text-xs text-muted">{formatPricingDays(rule.daysOfWeek)} · {formatTime(rule.startTime)}–{formatTime(rule.endTime)}</p>
          <RateGrid
            cacheCreation={rule.costRates.cacheCreationInputPerMillion}
            cached={rule.costRates.cachedInputPerMillion}
            output={rule.costRates.outputPerMillion}
            suffix={`${currency} / 百万 Token`}
            uncached={rule.costRates.uncachedInputPerMillion}
          />
        </div>
      ))}
    </div>
  );
}

function QuotaTimePolicySnapshot({ policy }: { policy?: QuotaTimePricingPolicy | null }) {
  if (!policy) {
    return <p className="rounded-lg bg-default/40 px-3 py-2.5 text-xs text-muted">套餐额度时段规则未启用。</p>;
  }
  return (
    <div className="space-y-3 rounded-lg border border-border p-3">
      <div>
        <p className="text-xs font-medium">套餐额度时段规则 · {policy.zoneId}</p>
        <p className="mt-1 text-xs text-muted">未命中规则倍率：×{formatDecimal(policy.defaultQuotaMultiplier)}</p>
      </div>
      {policy.rules.map((rule, index) => (
        <div className="flex flex-col gap-1 border-t border-separator pt-3 first:border-0 first:pt-0 sm:flex-row sm:items-center sm:justify-between" key={`${rule.name}:${index}`}>
          <div>
            <p className="text-sm font-medium">{rule.name}</p>
            <p className="mt-1 text-xs text-muted">{formatPricingDays(rule.daysOfWeek)} · {formatTime(rule.startTime)}–{formatTime(rule.endTime)}</p>
          </div>
          <Chip color="accent" size="sm" variant="soft">额度 ×{formatDecimal(rule.quotaMultiplier)}</Chip>
        </div>
      ))}
    </div>
  );
}

function VersionStatus({ status }: { status: ModelVersion["status"] }) {
  const labels = { DRAFT: "草稿", PUBLISHED: "线上", ARCHIVED: "已归档" } as const;
  const colors = { DRAFT: "warning", PUBLISHED: "success", ARCHIVED: "default" } as const;
  return <Chip color={colors[status]} size="sm" variant="soft">{labels[status]}</Chip>;
}

function Summary({ icon: Icon, label, value }: { icon: typeof Boxes; label: string; value: number }) {
  return (
    <Card variant="default">
      <Card.Content className="flex-row items-center gap-3">
        <span className="grid size-9 place-items-center rounded-xl bg-default text-muted"><Icon size={18} /></span>
        <div><p className="text-xs text-muted">{label}</p><p className="text-xl font-semibold">{value}</p></div>
      </Card.Content>
    </Card>
  );
}

function Info({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="min-w-0 rounded-xl bg-default/40 px-3 py-2.5">
      <p className="text-xs text-muted">{label}</p>
      <p className={`mt-1 truncate text-sm ${mono ? "font-mono text-xs" : ""}`} title={value}>{value}</p>
    </div>
  );
}

function emptyVersion(): ModelVersionInput {
  return {
    displayName: "",
    description: "",
    upstreamModel: "",
    contextWindow: 128000,
    maxOutputTokens: 8192,
    supportsReasoning: false,
    supportsTools: true,
    supportsVision: false,
    supportsJson: true,
    costCurrency: "USD",
    uncachedInputCostPerMillion: 0,
    cachedInputCostPerMillion: 0,
    outputCostPerMillion: 0,
    uncachedInputQuotaPerMillion: 1,
    cachedInputQuotaPerMillion: 0,
    outputQuotaPerMillion: 1,
    minimumRequestQuota: 0,
  };
}

function versionDefaults(version: ModelVersion): ModelVersionInput {
  return {
    displayName: version.displayName,
    description: version.description ?? "",
    upstreamModel: version.upstreamModel,
    contextWindow: version.capabilities.contextWindow,
    maxOutputTokens: version.capabilities.maxOutputTokens,
    supportsReasoning: version.capabilities.reasoning,
    supportsTools: version.capabilities.tools,
    supportsVision: version.capabilities.vision,
    supportsJson: version.capabilities.json,
    costCurrency: version.costCurrency,
    uncachedInputCostPerMillion: version.costRates.uncachedInputPerMillion,
    cachedInputCostPerMillion: version.costRates.cachedInputPerMillion,
    cacheCreationInputCostPerMillion: version.costRates.cacheCreationInputPerMillion || undefined,
    outputCostPerMillion: version.costRates.outputPerMillion,
    costTimePricingPolicy: version.costTimePricingPolicy ?? undefined,
    uncachedInputQuotaPerMillion: version.quotaRates.uncachedInputPerMillion,
    cachedInputQuotaPerMillion: version.quotaRates.cachedInputPerMillion,
    cacheCreationInputQuotaPerMillion: version.quotaRates.cacheCreationInputPerMillion || undefined,
    outputQuotaPerMillion: version.quotaRates.outputPerMillion,
    minimumRequestQuota: version.quotaRates.minimumRequestQuota,
    quotaTimePricingPolicy: version.quotaTimePricingPolicy ?? undefined,
  };
}

function defaultCostRates(defaults: ModelVersionInput): ModelRates {
  return {
    uncachedInputPerMillion: defaults.uncachedInputCostPerMillion,
    cachedInputPerMillion: defaults.cachedInputCostPerMillion,
    cacheCreationInputPerMillion: defaults.cacheCreationInputCostPerMillion ?? 0,
    outputPerMillion: defaults.outputCostPerMillion,
  };
}

function costTimePricingPolicyDefaults(defaults: ModelVersionInput): CostTimePricingPolicy {
  return defaults.costTimePricingPolicy ?? {
    zoneId: "Asia/Shanghai",
    rules: [],
  };
}

function quotaTimePricingPolicyDefaults(defaults: ModelVersionInput): QuotaTimePricingPolicy {
  return defaults.quotaTimePricingPolicy ?? {
    zoneId: "Asia/Shanghai",
    defaultQuotaMultiplier: 1,
    rules: [],
  };
}

function editableCostTimeRule(
  rule: CostTimePricingRule | undefined,
  baseRates: ModelRates,
  ordinal = 1,
): EditableCostTimePricingRule {
  const rates = rule?.costRates ?? baseRates;
  return {
    id: globalThis.crypto.randomUUID(),
    name: rule?.name ?? `工作日峰时 ${ordinal}`,
    daysOfWeek: rule?.daysOfWeek ?? ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
    startTime: rule?.startTime ?? "09:00",
    endTime: rule?.endTime ?? "12:00",
    cacheCreationEnabled: (rates.cacheCreationInputPerMillion ?? 0) > 0,
    costRates: { ...rates },
  };
}

function costTimeRuleInput(rule: EditableCostTimePricingRule): CostTimePricingRule {
  return {
    name: rule.name,
    daysOfWeek: rule.daysOfWeek,
    startTime: rule.startTime,
    endTime: rule.endTime,
    costRates: {
      ...rule.costRates,
      cacheCreationInputPerMillion: rule.cacheCreationEnabled
        ? rule.costRates.cacheCreationInputPerMillion
        : 0,
    },
  };
}

function editableQuotaTimeRule(
  rule: QuotaTimePricingRule | undefined,
  ordinal = 1,
): EditableQuotaTimePricingRule {
  return {
    id: globalThis.crypto.randomUUID(),
    name: rule?.name ?? `工作日峰时 ${ordinal}`,
    daysOfWeek: rule?.daysOfWeek ?? ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
    startTime: rule?.startTime ?? "09:00",
    endTime: rule?.endTime ?? "12:00",
    quotaMultiplier: rule?.quotaMultiplier ?? 1,
  };
}

function quotaTimeRuleInput(rule: EditableQuotaTimePricingRule): QuotaTimePricingRule {
  return {
    name: rule.name,
    daysOfWeek: rule.daysOfWeek,
    startTime: rule.startTime,
    endTime: rule.endTime,
    quotaMultiplier: rule.quotaMultiplier,
  };
}

function providerFor(providers: ModelProvider[], id?: number): ModelProvider | undefined {
  return providers.find((provider) => provider.id === id);
}

function protocolLabel(protocolType: string): string {
  const labels: Record<string, string> = {
    ANTHROPIC: "Anthropic Messages",
    OPENAI_COMPATIBLE: "Chat Completions",
    RESPONSES: "Responses",
  };
  return labels[protocolType] ?? protocolType;
}

function capabilitySummary(version: ModelVersion): string {
  const labels = [
    version.capabilities.reasoning ? "推理" : null,
    version.capabilities.tools ? "工具调用" : null,
    version.capabilities.vision ? "图片输入" : null,
    version.capabilities.json ? "JSON 输出" : null,
  ].filter(Boolean);
  return labels.length > 0 ? labels.join("、") : "基础文本";
}

function formatPricingDays(days: PricingDay[]): string {
  return pricingDays.filter((day) => days.includes(day.key)).map((day) => day.label).join("、");
}

function formatTime(value: string): string {
  return value.slice(0, 5);
}

function formatDecimal(value: number): string {
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 6 }).format(value);
}

function formatInteger(value: number): string {
  return new Intl.NumberFormat("zh-CN").format(value);
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function message(reason: unknown): string {
  return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试";
}
