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
  CircleDollarSign,
  CloudUpload,
  CopyPlus,
  History,
  PencilLine,
  Plus,
  Power,
  RefreshCw,
  Server,
  SlidersHorizontal,
} from "lucide-react";
import { useEffect, useMemo, useState, type FormEvent } from "react";

import { ApiClientError } from "../../api/auth";
import {
  createModel,
  createModelDraft,
  listModels,
  listModelVersions,
  listProviders,
  publishModelDraft,
  updateModelDraft,
  updateModelStatus,
  type AdminModel,
  type ModelProvider,
  type ModelVersion,
  type ModelVersionInput,
} from "../../api/catalog";

type VersionRateKey = keyof Pick<
  ModelVersionInput,
  | "inputCostPerMillion"
  | "outputCostPerMillion"
  | "reasoningCostPerMillion"
  | "cacheReadCostPerMillion"
  | "cacheWriteCostPerMillion"
  | "inputQuotaPerMillion"
  | "outputQuotaPerMillion"
  | "reasoningQuotaPerMillion"
  | "cacheReadQuotaPerMillion"
  | "cacheWriteQuotaPerMillion"
  | "minimumRequestQuota"
>;

const costFields: Array<{ name: VersionRateKey; label: string }> = [
  { name: "inputCostPerMillion", label: "输入 Token" },
  { name: "outputCostPerMillion", label: "输出 Token" },
  { name: "reasoningCostPerMillion", label: "推理 Token" },
  { name: "cacheReadCostPerMillion", label: "缓存读取" },
  { name: "cacheWriteCostPerMillion", label: "缓存写入" },
];

const quotaFields: Array<{ name: VersionRateKey; label: string }> = [
  { name: "inputQuotaPerMillion", label: "输入 Token" },
  { name: "outputQuotaPerMillion", label: "输出 Token" },
  { name: "reasoningQuotaPerMillion", label: "推理 Token" },
  { name: "cacheReadQuotaPerMillion", label: "缓存读取" },
  { name: "cacheWriteQuotaPerMillion", label: "缓存写入" },
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
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const providerId = numberValue(form, "providerId");
    const version = versionInput(form);
    const operation = selectedModel ? `save:${selectedModel.modelId}` : "create";
    setPending(operation);
    clearFeedback();
    try {
      const updated = selectedModel
        ? await updateModelDraft(selectedModel, providerId, version)
        : await createModel({ code: textValue(form, "code"), providerId, version });
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
              provider={providerFor(providers, model.draft?.providerId ?? model.published?.providerId)}
              onEdit={() => setSelectedModelId(model.modelId)}
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
          providers={activeProviders}
          onCancel={selectedModel ? beginCreate : undefined}
          onSubmit={save}
        />
      </section>
    </div>
  );
}

function ModelCard({
  model,
  provider,
  history,
  historyOpen,
  pending,
  onEdit,
  onStartDraft,
  onPublish,
  onToggleStatus,
  onHistory,
}: {
  model: AdminModel;
  provider?: ModelProvider;
  history?: ModelVersion[];
  historyOpen: boolean;
  pending: string | null;
  onEdit: () => void;
  onStartDraft: () => void;
  onPublish: () => void;
  onToggleStatus: () => void;
  onHistory: () => void;
}) {
  const current = model.draft ?? model.published;
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
          <Button isDisabled={isBusy} onPress={onHistory} size="sm" variant="tertiary">
            <History size={15} /> {historyOpen ? "收起版本" : "版本记录"}
          </Button>
        </div>

        {historyOpen && (
          <VersionHistory loading={pending === `history:${model.modelId}`} versions={history} />
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
}: {
  model: AdminModel | null;
  providers: ModelProvider[];
  pending: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel?: () => void;
}) {
  const draft = model?.draft;
  const defaults = draft ? versionDefaults(draft) : emptyVersion();
  const canEdit = !model || Boolean(draft);

  return (
    <Card className="xl:sticky xl:top-24" variant="default">
      <Card.Header>
        <span className="grid size-9 place-items-center rounded-xl bg-default text-muted">
          {model ? <PencilLine size={18} /> : <Plus size={18} />}
        </span>
        <div>
          <Card.Title>{model ? "编辑模型草稿" : "新增模型"}</Card.Title>
          <Card.Description>
            {model ? `${model.code} · 草稿 v${draft?.versionNo ?? "-"}` : "创建后先进入草稿状态"}
          </Card.Description>
        </div>
      </Card.Header>
      <Card.Content>
        {!canEdit ? (
          <div className="rounded-xl bg-default/50 p-4 text-sm text-muted">
            当前模型没有草稿，请先在左侧点击“从线上版本创建草稿”。
          </div>
        ) : (
          <form className="space-y-6" onSubmit={onSubmit}>
            <EditorSection icon={SlidersHorizontal} title="基础信息">
              {!model && (
                <TextField fullWidth isRequired name="code">
                  <Label>模型编码</Label>
                  <Input fullWidth placeholder="例如 gpt-4o-mini" />
                </TextField>
              )}
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField fullWidth isRequired name="displayName">
                  <Label>显示名称</Label>
                  <Input defaultValue={defaults.displayName} fullWidth placeholder="例如 GPT-4o mini" />
                </TextField>
                <TextField fullWidth isRequired name="upstreamModel">
                  <Label>上游模型 ID</Label>
                  <Input defaultValue={defaults.upstreamModel} fullWidth placeholder="例如 gpt-4o-mini" />
                </TextField>
              </div>
              <TextField fullWidth name="description">
                <Label>模型说明</Label>
                <TextArea defaultValue={defaults.description} fullWidth placeholder="面向用户展示的简短说明" rows={2} />
              </TextField>
              <Select
                defaultSelectedKey={String(draft?.providerId ?? providers[0]?.id ?? "")}
                fullWidth
                isRequired
                name="providerId"
                placeholder="请选择供应商"
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
                          <p className="truncate text-xs text-muted">{protocolLabel(provider.protocolType)} · {provider.baseUrl}</p>
                        </div>
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>
              <div className="grid gap-4 sm:grid-cols-2">
                <NumericField defaultValue={defaults.contextWindow} label="上下文窗口" name="contextWindow" step="1" />
                <NumericField defaultValue={defaults.maxOutputTokens} label="最大输出 Token" name="maxOutputTokens" step="1" />
              </div>
            </EditorSection>

            <EditorSection icon={CheckCircle2} title="模型能力">
              <div className="grid gap-3 sm:grid-cols-2">
                <Capability defaultSelected={defaults.supportsReasoning} label="推理 / Thinking" name="supportsReasoning" />
                <Capability defaultSelected={defaults.supportsTools} label="工具调用" name="supportsTools" />
                <Capability defaultSelected={defaults.supportsVision} label="图片输入" name="supportsVision" />
                <Capability defaultSelected={defaults.supportsJson} label="JSON 输出" name="supportsJson" />
              </div>
            </EditorSection>

            <EditorSection icon={CircleDollarSign} title="上游成本">
              <div className="grid gap-4 sm:grid-cols-2">
                <Select
                  defaultSelectedKey={defaults.costCurrency}
                  fullWidth
                  isRequired
                  name="costCurrency"
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
              <p className="text-xs text-muted">以下价格均为每 100 万 Token 的真实供应商成本。</p>
              <div className="grid gap-4 sm:grid-cols-2">
                {costFields.map((field) => (
                  <NumericField
                    defaultValue={defaults[field.name]}
                    key={field.name}
                    label={field.label}
                    name={field.name}
                  />
                ))}
              </div>
            </EditorSection>

            <EditorSection icon={Archive} title="套餐额度计费">
              <p className="text-xs leading-5 text-muted">
                额度费率与上游成本分开配置。至少一个费率或最低请求额度必须大于 0。
              </p>
              <div className="grid gap-4 sm:grid-cols-2">
                {quotaFields.map((field) => (
                  <NumericField
                    defaultValue={defaults[field.name]}
                    key={field.name}
                    label={`${field.label} / 百万`}
                    name={field.name}
                  />
                ))}
                <NumericField
                  defaultValue={defaults.minimumRequestQuota}
                  label="单次最低额度"
                  name="minimumRequestQuota"
                />
              </div>
            </EditorSection>

            <div className="flex justify-end gap-2">
              {onCancel && <Button onPress={onCancel} variant="tertiary">取消编辑</Button>}
              <Button fullWidth={!onCancel} isDisabled={pending || providers.length === 0} type="submit" variant="primary">
                {pending ? "正在保存…" : model ? "保存草稿" : "创建模型草稿"}
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

function Capability({ name, label, defaultSelected }: { name: string; label: string; defaultSelected: boolean }) {
  return (
    <Checkbox defaultSelected={defaultSelected} name={name} value="true">
      <Checkbox.Content>
        <Checkbox.Control><Checkbox.Indicator /></Checkbox.Control>
        <Label>{label}</Label>
      </Checkbox.Content>
    </Checkbox>
  );
}

function NumericField({ name, label, defaultValue, step = "0.000001" }: {
  name: string;
  label: string;
  defaultValue: number;
  step?: string;
}) {
  return (
    <TextField fullWidth isRequired name={name}>
      <Label>{label}</Label>
      <Input defaultValue={String(defaultValue)} fullWidth min="0" step={step} type="number" />
    </TextField>
  );
}

function VersionHistory({ versions, loading }: { versions?: ModelVersion[]; loading: boolean }) {
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
            <div className="flex flex-col gap-2 border-b border-separator pb-3 last:border-0 last:pb-0 sm:flex-row sm:items-center" key={version.id}>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">v{version.versionNo}</span>
                  <VersionStatus status={version.status} />
                </div>
                <p className="mt-1 truncate text-xs text-muted">
                  {version.upstreamModel} · {formatDate(version.publishedAt ?? version.createdAt)}
                </p>
              </div>
              <span className="font-mono text-xs text-muted" title={version.pricingVersion}>
                {version.pricingVersion.slice(0, 8)}
              </span>
            </div>
          ))}
        </div>
      )}
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
    inputCostPerMillion: 0,
    outputCostPerMillion: 0,
    reasoningCostPerMillion: 0,
    cacheReadCostPerMillion: 0,
    cacheWriteCostPerMillion: 0,
    inputQuotaPerMillion: 1,
    outputQuotaPerMillion: 1,
    reasoningQuotaPerMillion: 0,
    cacheReadQuotaPerMillion: 0,
    cacheWriteQuotaPerMillion: 0,
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
    inputCostPerMillion: version.costRates.inputPerMillion,
    outputCostPerMillion: version.costRates.outputPerMillion,
    reasoningCostPerMillion: version.costRates.reasoningPerMillion,
    cacheReadCostPerMillion: version.costRates.cacheReadPerMillion,
    cacheWriteCostPerMillion: version.costRates.cacheWritePerMillion,
    inputQuotaPerMillion: version.quotaRates.inputPerMillion,
    outputQuotaPerMillion: version.quotaRates.outputPerMillion,
    reasoningQuotaPerMillion: version.quotaRates.reasoningPerMillion,
    cacheReadQuotaPerMillion: version.quotaRates.cacheReadPerMillion,
    cacheWriteQuotaPerMillion: version.quotaRates.cacheWritePerMillion,
    minimumRequestQuota: version.quotaRates.minimumRequestQuota,
  };
}

function versionInput(form: FormData): ModelVersionInput {
  return {
    displayName: textValue(form, "displayName"),
    description: textValue(form, "description"),
    upstreamModel: textValue(form, "upstreamModel"),
    contextWindow: numberValue(form, "contextWindow"),
    maxOutputTokens: numberValue(form, "maxOutputTokens"),
    supportsReasoning: form.has("supportsReasoning"),
    supportsTools: form.has("supportsTools"),
    supportsVision: form.has("supportsVision"),
    supportsJson: form.has("supportsJson"),
    costCurrency: textValue(form, "costCurrency"),
    inputCostPerMillion: numberValue(form, "inputCostPerMillion"),
    outputCostPerMillion: numberValue(form, "outputCostPerMillion"),
    reasoningCostPerMillion: numberValue(form, "reasoningCostPerMillion"),
    cacheReadCostPerMillion: numberValue(form, "cacheReadCostPerMillion"),
    cacheWriteCostPerMillion: numberValue(form, "cacheWriteCostPerMillion"),
    inputQuotaPerMillion: numberValue(form, "inputQuotaPerMillion"),
    outputQuotaPerMillion: numberValue(form, "outputQuotaPerMillion"),
    reasoningQuotaPerMillion: numberValue(form, "reasoningQuotaPerMillion"),
    cacheReadQuotaPerMillion: numberValue(form, "cacheReadQuotaPerMillion"),
    cacheWriteQuotaPerMillion: numberValue(form, "cacheWriteQuotaPerMillion"),
    minimumRequestQuota: numberValue(form, "minimumRequestQuota"),
  };
}

function textValue(form: FormData, name: string): string {
  return String(form.get(name) ?? "").trim();
}

function numberValue(form: FormData, name: string): number {
  return Number(textValue(form, name));
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

function formatInteger(value: number): string {
  return new Intl.NumberFormat("zh-CN").format(value);
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function message(reason: unknown): string {
  return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试";
}
