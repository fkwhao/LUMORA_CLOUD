import { Button, Card, Chip, Input, Label, ListBox, Select, TextField } from "@heroui/react";
import { ArrowLeft, CheckCircle2, KeyRound, Plus, RefreshCw, RotateCcw, Server, SlidersHorizontal } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";

import { ApiClientError } from "../../api/auth";
import {
  createProvider,
  listProviders,
  rotateProviderCredential,
  updateProvider,
  type ModelProvider,
} from "../../api/catalog";

const protocolOptions = [
  {
    id: "ANTHROPIC",
    desktopValue: "anthropic",
    label: "Anthropic Messages (/v1/messages)",
  },
  {
    id: "OPENAI_COMPATIBLE",
    desktopValue: "chat-completions",
    label: "Chat Completions (/chat/completions)",
  },
  {
    id: "RESPONSES",
    desktopValue: "responses",
    label: "Responses (/responses)",
  },
] as const;

export function ModelProvidersPage() {
  const [providers, setProviders] = useState<ModelProvider[]>([]);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [rotatingId, setRotatingId] = useState<number | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setProviders(await listProviders());
    } catch (reason) {
      setError(message(reason));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function submitProvider(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setPending(true);
    setError(null);
    setNotice(null);
    try {
      const created = await createProvider({
        code: String(form.get("code") ?? ""),
        name: String(form.get("name") ?? ""),
        protocolType: String(form.get("protocolType") ?? "OPENAI_COMPATIBLE"),
        baseUrl: String(form.get("baseUrl") ?? ""),
        apiKey: String(form.get("apiKey") ?? ""),
        maxConcurrency: optionalNumber(form, "maxConcurrency"),
        requestsPerMinute: optionalNumber(form, "requestsPerMinute"),
        tokensPerMinute: optionalNumber(form, "tokensPerMinute"),
      });
      setProviders((current) => [...current, created].sort((left, right) => left.id - right.id));
      formElement.reset();
      setNotice(`${created.name} 已创建，API Key 已加密保存。`);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(false);
    }
  }

  async function updateLimits(event: FormEvent<HTMLFormElement>, provider: ModelProvider) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await updateProvider(provider, {
        name: provider.name,
        protocolType: provider.protocolType,
        baseUrl: provider.baseUrl,
        status: provider.status,
        maxConcurrency: optionalNumber(form, "maxConcurrency"),
        requestsPerMinute: optionalNumber(form, "requestsPerMinute"),
        tokensPerMinute: optionalNumber(form, "tokensPerMinute"),
      });
      setProviders((current) => current.map((item) => item.id === updated.id ? updated : item));
      setEditingId(null);
      setNotice(`${updated.name} 的账号容量已更新。`);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(false);
    }
  }

  async function rotate(event: FormEvent<HTMLFormElement>, provider: ModelProvider) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const apiKey = String(new FormData(formElement).get("apiKey") ?? "");
    setPending(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await rotateProviderCredential(provider, apiKey);
      setProviders((current) => current.map((item) => item.id === updated.id ? updated : item));
      formElement.reset();
      setRotatingId(null);
      setNotice(`${updated.name} 的 API Key 已完成轮换。`);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-1 text-sm text-muted">模型目录</p>
          <h1 className="text-2xl font-semibold tracking-tight">供应商账号</h1>
          <p className="mt-2 max-w-2xl text-sm text-muted">
            每条记录代表一个独立上游账号及其 API Key。同一模型可绑定多个账号路由并按权重分流。
          </p>
        </div>
        <div className="flex gap-2">
          <Button onPress={() => window.location.assign("/admin/models")} variant="secondary">
            <ArrowLeft size={16} /> 返回模型目录
          </Button>
          <Button isDisabled={loading} onPress={() => void load()} variant="secondary">
            <RefreshCw size={16} /> 刷新
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

      <section className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(340px,.65fr)]">
        <div className="space-y-4">
          {loading ? (
            <Card variant="default"><Card.Content className="py-10 text-center text-sm text-muted">正在读取供应商…</Card.Content></Card>
          ) : providers.length === 0 ? (
            <Card variant="default">
              <Card.Content className="items-center py-10 text-center">
                <Server className="text-muted" size={24} />
                <p className="mt-3 text-sm font-medium">还没有模型供应商</p>
                <p className="mt-1 text-xs text-muted">从右侧创建第一个模型供应商。</p>
              </Card.Content>
            </Card>
          ) : providers.map((provider) => (
            <Card key={provider.id} variant="default">
              <Card.Header className="flex-row items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <Card.Title>{provider.name}</Card.Title>
                    <Chip color={provider.status === "ACTIVE" ? "success" : "default"} size="sm" variant="soft">
                      {provider.status === "ACTIVE" ? "已启用" : "已停用"}
                    </Chip>
                  </div>
                  <Card.Description className="mt-1">
                    {provider.code} · {protocolLabel(provider.protocolType)}
                  </Card.Description>
                </div>
                <div className="flex gap-2">
                  <Button isDisabled={pending} onPress={() => setEditingId((current) => current === provider.id ? null : provider.id)} size="sm" variant="tertiary">
                    <SlidersHorizontal size={15} /> 容量
                  </Button>
                  <Button isDisabled={pending} onPress={() => setRotatingId((current) => current === provider.id ? null : provider.id)} size="sm" variant="secondary">
                    <RotateCcw size={15} /> 轮换 Key
                  </Button>
                </div>
              </Card.Header>
              <Card.Content className="gap-4 pt-1">
                <div className="grid gap-3 text-sm sm:grid-cols-2">
                  <Info label="API Base URL" value={provider.baseUrl} mono />
                  <Info
                    label="凭据存储"
                    value={provider.credential.managed ? "服务端加密数据库" : "旧版环境变量引用"}
                  />
                  <Info label="Key 掩码" value={provider.credential.maskedValue} mono />
                  <Info label="指纹" value={provider.credential.fingerprint ?? "未提供"} mono />
                  <Info label="账号总并发" value={capacity(provider.maxConcurrency, "不限")} />
                  <Info label="账号 RPM / TPM" value={`${capacity(provider.requestsPerMinute, "不限")} / ${capacity(provider.tokensPerMinute, "不限")}`} />
                </div>

                {editingId === provider.id && (
                  <form className="rounded-xl border border-border bg-default/30 p-4" onSubmit={(event) => void updateLimits(event, provider)}>
                    <div className="mb-3 flex items-center gap-2 text-sm font-medium"><SlidersHorizontal size={16} /> 供应商账号容量</div>
                    <p className="mb-4 text-xs leading-5 text-muted">留空表示平台不额外限制；应填写供应商为该账号实际授予的限制。</p>
                    <div className="grid gap-3 sm:grid-cols-3">
                      <OptionalCapacityField defaultValue={provider.maxConcurrency} label="总并发" name="maxConcurrency" />
                      <OptionalCapacityField defaultValue={provider.requestsPerMinute} label="RPM" name="requestsPerMinute" />
                      <OptionalCapacityField defaultValue={provider.tokensPerMinute} label="TPM" name="tokensPerMinute" />
                    </div>
                    <div className="mt-3 flex justify-end gap-2">
                      <Button onPress={() => setEditingId(null)} size="sm" variant="tertiary">取消</Button>
                      <Button isDisabled={pending} size="sm" type="submit" variant="primary">保存容量</Button>
                    </div>
                  </form>
                )}

                {rotatingId === provider.id && (
                  <form className="rounded-xl border border-border bg-default/30 p-4" onSubmit={(event) => void rotate(event, provider)}>
                    <div className="mb-3 flex items-center gap-2 text-sm font-medium"><KeyRound size={16} /> 输入新的 API Key</div>
                    <TextField fullWidth isRequired name="apiKey" type="password">
                      <Label>新 API Key</Label>
                      <Input fullWidth autoComplete="new-password" placeholder="仅本次提交可见" />
                    </TextField>
                    <div className="mt-3 flex justify-end gap-2">
                      <Button onPress={() => setRotatingId(null)} size="sm" variant="tertiary">取消</Button>
                      <Button isDisabled={pending} size="sm" type="submit" variant="primary">
                        {pending ? "正在轮换…" : "确认轮换"}
                      </Button>
                    </div>
                  </form>
                )}
              </Card.Content>
            </Card>
          ))}
        </div>

        <Card className="xl:sticky xl:top-24" variant="default">
          <Card.Header>
            <span className="grid size-9 place-items-center rounded-xl bg-default text-muted"><Plus size={18} /></span>
            <div><Card.Title>新增供应商</Card.Title><Card.Description>保存基础连接信息和第一把 API Key</Card.Description></div>
          </Card.Header>
          <Card.Content>
            <form className="space-y-4" onSubmit={submitProvider}>
              <TextField fullWidth isRequired name="name">
                <Label>显示名称</Label><Input fullWidth placeholder="例如 OpenAI" />
              </TextField>
              <TextField fullWidth isRequired name="code">
                <Label>供应商编码</Label><Input fullWidth placeholder="例如 openai" />
              </TextField>
              <Select
                fullWidth
                isRequired
                name="protocolType"
                placeholder="请选择 API 格式"
                defaultSelectedKey="OPENAI_COMPATIBLE"
                variant="secondary"
              >
                <Label>API 格式</Label>
                <Select.Trigger>
                  <Select.Value>{({ selectedText }) => selectedText}</Select.Value>
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {protocolOptions.map((option) => (
                      <ListBox.Item
                        key={option.id}
                        id={option.id}
                        textValue={option.label}
                      >
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-medium">{option.label}</p>
                          <p className="truncate text-xs text-muted">
                            Desktop 值：{option.desktopValue} · Cloud 已支持
                          </p>
                        </div>
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>
              <TextField fullWidth isRequired name="baseUrl" type="url">
                <Label>API Base URL</Label><Input fullWidth placeholder="https://api.example.com/v1" />
              </TextField>
              <TextField fullWidth isRequired name="apiKey" type="password">
                <Label>API Key</Label><Input fullWidth autoComplete="new-password" placeholder="提交后不再回显" />
              </TextField>
              <div className="grid gap-4 sm:grid-cols-3 xl:grid-cols-1 2xl:grid-cols-3">
                <OptionalCapacityField label="账号总并发" name="maxConcurrency" />
                <OptionalCapacityField label="账号 RPM" name="requestsPerMinute" />
                <OptionalCapacityField label="账号 TPM" name="tokensPerMinute" />
              </div>
              <p className="flex gap-2 text-xs leading-5 text-muted"><CheckCircle2 className="mt-0.5 shrink-0" size={14} />密钥不会写入 Nacos、浏览器存储、模型发布快照或应用日志。</p>
              <Button fullWidth isDisabled={pending} type="submit" variant="primary">
                {pending ? "正在保存…" : "创建供应商"}
              </Button>
            </form>
          </Card.Content>
        </Card>
      </section>
    </div>
  );
}

function OptionalCapacityField({ name, label, defaultValue }: { name: string; label: string; defaultValue?: number | null }) {
  return (
    <TextField fullWidth name={name} type="number">
      <Label>{label}（可选）</Label>
      <Input defaultValue={defaultValue ?? ""} fullWidth min="1" placeholder="不限" />
    </TextField>
  );
}

function optionalNumber(form: FormData, name: string): number | undefined {
  const value = String(form.get(name) ?? "").trim();
  return value ? Number(value) : undefined;
}

function capacity(value: number | null | undefined, fallback: string): string {
  return value == null ? fallback : new Intl.NumberFormat("zh-CN").format(value);
}

function Info({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="min-w-0 rounded-xl bg-default/40 px-3 py-2.5">
      <p className="text-xs text-muted">{label}</p>
      <p className={`mt-1 truncate text-sm ${mono ? "font-mono text-xs" : ""}`} title={value}>{value}</p>
    </div>
  );
}

function protocolLabel(protocolType: string): string {
  return protocolOptions.find((option) => option.id === protocolType)?.label ?? protocolType;
}

function message(reason: unknown): string {
  return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试";
}
