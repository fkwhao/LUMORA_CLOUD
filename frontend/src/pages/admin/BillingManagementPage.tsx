import {
  Button,
  Card,
  Chip,
  Input,
  Label,
  ListBox,
  Select,
  TextArea,
  TextField,
} from "@heroui/react";
import {
  CalendarPlus,
  CircleDollarSign,
  Clock3,
  History,
  PackagePlus,
  Plus,
  RefreshCw,
  ShoppingBag,
  Sparkles,
  UserPlus,
  WalletCards,
} from "lucide-react";
import { useEffect, useMemo, useState, type FormEvent } from "react";

import { ApiClientError } from "../../api/auth";
import {
  createBillingPlan,
  grantSubscription,
  listAdminPlans,
  listAdminOrders,
  listAdminSubscriptions,
  listPlanVersions,
  publishPlanVersion,
  type BillingPlan,
  type BillingSubscription,
  type PurchaseOrder,
} from "../../api/billing";
import type { AdminUser } from "../../api/users";
import { useAdminUserSearch } from "../../features/admin/useAdminUserSearch";

export function BillingManagementPage() {
  const [plans, setPlans] = useState<BillingPlan[]>([]);
  const [subscriptions, setSubscriptions] = useState<BillingSubscription[]>([]);
  const [orders, setOrders] = useState<PurchaseOrder[]>([]);
  const {
    query: userQuery,
    setQuery: setUserQuery,
    users,
    loading: userSearchLoading,
    loadingMore: userSearchLoadingMore,
    error: userSearchError,
    hasMore: hasMoreUsers,
    queryIsValid: userQueryIsValid,
    reload: reloadUsers,
    loadMore: loadMoreUsers,
  } = useAdminUserSearch();
  const [versions, setVersions] = useState<Record<number, BillingPlan[]>>({});
  const [historyPlanId, setHistoryPlanId] = useState<number | null>(null);
  const [versionPlanId, setVersionPlanId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState<string | null>(null);
  const [formVersion, setFormVersion] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const versionPlan = useMemo(
    () => plans.find((plan) => plan.planId === versionPlanId) ?? null,
    [plans, versionPlanId],
  );

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [planData, subscriptionData, orderData] = await Promise.all([
        listAdminPlans(),
        listAdminSubscriptions(),
        listAdminOrders(),
      ]);
      setPlans(planData);
      setSubscriptions(subscriptionData);
      setOrders(orderData);
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

  async function createPlan(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setPending("create-plan");
    clearFeedback();
    try {
      const created = await createBillingPlan({
        code: textValue(form, "code"),
        name: textValue(form, "name"),
        description: textValue(form, "description"),
        monthlyPriceMinor: minorUnits(form, "monthlyPrice"),
        currency: textValue(form, "currency"),
        weeklyQuota: numberValue(form, "weeklyQuota"),
      });
      upsertPlan(created);
      formElement.reset();
      setFormVersion((current) => current + 1);
      setNotice(`${created.name} v1 已创建并发布。`);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  async function createVersion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!versionPlan) return;
    const form = new FormData(event.currentTarget);
    setPending(`version:${versionPlan.planId}`);
    clearFeedback();
    try {
      const published = await publishPlanVersion(versionPlan.planId, {
        monthlyPriceMinor: minorUnits(form, "monthlyPrice"),
        currency: textValue(form, "currency"),
        weeklyQuota: numberValue(form, "weeklyQuota"),
      });
      upsertPlan(published);
      setVersions((current) => {
        const next = { ...current };
        delete next[published.planId];
        return next;
      });
      setVersionPlanId(null);
      setNotice(`${published.name} v${published.versionNo} 已发布，新订阅将使用该版本。`);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  async function grant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending("grant");
    clearFeedback();
    try {
      const created = await grantSubscription({
        userId: numberValue(form, "userId"),
        planVersionId: numberValue(form, "planVersionId"),
        sourceReference: textValue(form, "sourceReference"),
        startsAt: localDateTimeToIso(textValue(form, "startsAt")),
        endsAt: localDateTimeToIso(textValue(form, "endsAt")),
      });
      setSubscriptions((current) => [created, ...current.filter((item) => item.subscriptionId !== created.subscriptionId)]);
      setFormVersion((current) => current + 1);
      setNotice(`套餐已发放给用户 #${created.userId}。重复提交同一发放引用不会重复创建。`);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  async function toggleHistory(plan: BillingPlan) {
    if (historyPlanId === plan.planId) {
      setHistoryPlanId(null);
      return;
    }
    setHistoryPlanId(plan.planId);
    if (versions[plan.planId]) return;
    setPending(`history:${plan.planId}`);
    clearFeedback();
    try {
      const data = await listPlanVersions(plan.planId);
      setVersions((current) => ({ ...current, [plan.planId]: data }));
    } catch (reason) {
      setHistoryPlanId(null);
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  function upsertPlan(updated: BillingPlan) {
    setPlans((current) => {
      const exists = current.some((plan) => plan.planId === updated.planId);
      return exists
        ? current.map((plan) => plan.planId === updated.planId ? updated : plan)
        : [...current, updated].sort((left, right) => left.planId - right.planId);
    });
  }

  function clearFeedback() {
    setError(null);
    setNotice(null);
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-1 text-sm text-muted">套餐与计费</p>
          <h1 className="text-2xl font-semibold tracking-tight">套餐版本与订阅发放</h1>
          <p className="mt-2 max-w-2xl text-sm text-muted">
            套餐价格和周额度按版本冻结；已存在的订阅不会被后续版本修改。
          </p>
        </div>
        <Button isDisabled={loading || userSearchLoading} onPress={() => { void load(); void reloadUsers(); }} variant="secondary">
          <RefreshCw size={16} /> 刷新
        </Button>
      </header>

      {(error || userSearchError || notice) && (
        <div
          className={`rounded-xl px-4 py-3 text-sm ${error || userSearchError ? "bg-danger-soft text-danger" : "bg-success-soft text-success"}`}
          role={error || userSearchError ? "alert" : "status"}
        >
          {error ?? userSearchError ?? notice}
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Summary icon={WalletCards} label="已发布套餐" value={plans.length} />
        <Summary icon={Sparkles} label="有效订阅" value={subscriptions.filter(isCurrentlyActive).length} />
        <Summary icon={CalendarPlus} label="最近发放记录" value={subscriptions.length} />
        <Summary icon={ShoppingBag} label="最近订单" value={orders.length} />
      </section>

      <section className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(380px,.85fr)]">
        <div className="space-y-4">
          {loading ? (
            <Card variant="default"><Card.Content className="py-10 text-center text-sm text-muted">正在读取套餐目录…</Card.Content></Card>
          ) : plans.length === 0 ? (
            <Card variant="default">
              <Card.Content className="items-center py-12 text-center">
                <WalletCards className="text-muted" size={26} />
                <p className="mt-3 text-sm font-medium">还没有套餐</p>
                <p className="mt-1 text-xs text-muted">从右侧创建第一个套餐，v1 会立即发布。</p>
              </Card.Content>
            </Card>
          ) : plans.map((plan) => (
            <Card key={plan.planId} variant="default">
              <Card.Header className="flex-row items-start justify-between gap-4">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <Card.Title>{plan.name}</Card.Title>
                    <Chip color="success" size="sm" variant="soft">已发布 v{plan.versionNo}</Chip>
                  </div>
                  <Card.Description className="mt-1">{plan.code} · 版本 ID {plan.planVersionId}</Card.Description>
                </div>
                <strong className="text-lg font-semibold">{formatMoney(plan.monthlyPriceMinor, plan.currency)}<small className="text-xs font-normal text-muted"> / 月</small></strong>
              </Card.Header>
              <Card.Content className="gap-4 pt-1">
                <p className="text-sm text-muted">{plan.description || "暂无套餐说明"}</p>
                <div className="grid gap-3 sm:grid-cols-2">
                  <Info label="每周额度" value={formatQuota(plan.weeklyQuota)} />
                  <Info label="当前发布版本" value={`v${plan.versionNo}`} />
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button
                    isDisabled={pending !== null}
                    onPress={() => setVersionPlanId(plan.planId)}
                    size="sm"
                    variant="primary"
                  >
                    <Plus size={15} /> 发布新版本
                  </Button>
                  <Button
                    isDisabled={pending !== null}
                    onPress={() => void toggleHistory(plan)}
                    size="sm"
                    variant="tertiary"
                  >
                    <History size={15} /> {historyPlanId === plan.planId ? "收起版本" : "历史版本"}
                  </Button>
                </div>
                {historyPlanId === plan.planId && (
                  <PlanHistory loading={pending === `history:${plan.planId}`} versions={versions[plan.planId]} />
                )}
              </Card.Content>
            </Card>
          ))}
        </div>

        <div className="space-y-6 xl:sticky xl:top-24">
          {versionPlan ? (
            <PlanVersionForm
              key={`${versionPlan.planVersionId}:${versionPlan.versionNo}`}
              pending={pending !== null}
              plan={versionPlan}
              onCancel={() => setVersionPlanId(null)}
              onSubmit={createVersion}
            />
          ) : (
            <CreatePlanForm key={formVersion} pending={pending !== null} onSubmit={createPlan} />
          )}

          <GrantForm
            key={`${formVersion}:${plans.map((plan) => plan.planVersionId).join("-")}`}
            hasMoreUsers={hasMoreUsers}
            onLoadMoreUsers={() => void loadMoreUsers()}
            onSubmit={grant}
            onUserQueryChange={setUserQuery}
            pending={pending !== null || userSearchLoading}
            plans={plans}
            userQuery={userQuery}
            userQueryIsValid={userQueryIsValid}
            userSearchLoading={userSearchLoading}
            userSearchLoadingMore={userSearchLoadingMore}
            users={users}
          />
        </div>
      </section>

      <RecentSubscriptions plans={plans} subscriptions={subscriptions} users={users} />
      <RecentOrders orders={orders} users={users} />
    </div>
  );
}

function CreatePlanForm({ pending, onSubmit }: { pending: boolean; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  return (
    <Card variant="default">
      <Card.Header>
        <span className="grid size-9 place-items-center rounded-xl bg-default text-muted"><PackagePlus size={18} /></span>
        <div><Card.Title>新增套餐</Card.Title><Card.Description>创建时直接发布 v1</Card.Description></div>
      </Card.Header>
      <Card.Content>
        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField fullWidth isRequired name="name"><Label>套餐名称</Label><Input fullWidth placeholder="例如 Lumora Pro" /></TextField>
            <TextField fullWidth isRequired name="code"><Label>套餐编码</Label><Input fullWidth placeholder="例如 lumora-pro" /></TextField>
          </div>
          <TextField fullWidth name="description"><Label>套餐说明</Label><TextArea fullWidth placeholder="面向用户展示的权益说明" rows={2} /></TextField>
          <PriceAndQuotaFields />
          <Button fullWidth isDisabled={pending} type="submit" variant="primary">
            {pending ? "正在保存…" : "创建并发布套餐"}
          </Button>
        </form>
      </Card.Content>
    </Card>
  );
}

function PlanVersionForm({ plan, pending, onSubmit, onCancel }: {
  plan: BillingPlan;
  pending: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
}) {
  return (
    <Card variant="default">
      <Card.Header>
        <span className="grid size-9 place-items-center rounded-xl bg-default text-muted"><CircleDollarSign size={18} /></span>
        <div><Card.Title>发布 {plan.name} v{plan.versionNo + 1}</Card.Title><Card.Description>旧版本继续服务已有订阅</Card.Description></div>
      </Card.Header>
      <Card.Content>
        <form className="space-y-4" onSubmit={onSubmit}>
          <PriceAndQuotaFields plan={plan} />
          <div className="flex justify-end gap-2">
            <Button onPress={onCancel} variant="tertiary">取消</Button>
            <Button isDisabled={pending} type="submit" variant="primary">{pending ? "正在发布…" : "确认发布新版本"}</Button>
          </div>
        </form>
      </Card.Content>
    </Card>
  );
}

function PriceAndQuotaFields({ plan }: { plan?: BillingPlan }) {
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField fullWidth isRequired name="monthlyPrice">
          <Label>月价格</Label>
          <Input defaultValue={plan ? String(plan.monthlyPriceMinor / 100) : "0"} fullWidth min="0" step="0.01" type="number" />
        </TextField>
        <Select defaultSelectedKey={plan?.currency ?? "CNY"} fullWidth isRequired name="currency" variant="secondary">
          <Label>币种</Label>
          <Select.Trigger><Select.Value /><Select.Indicator /></Select.Trigger>
          <Select.Popover><ListBox><ListBox.Item id="CNY">CNY</ListBox.Item><ListBox.Item id="USD">USD</ListBox.Item></ListBox></Select.Popover>
        </Select>
      </div>
      <TextField fullWidth isRequired name="weeklyQuota">
        <Label>每周套餐额度</Label>
        <Input defaultValue={plan ? String(plan.weeklyQuota) : "100"} fullWidth min="0.000001" step="0.000001" type="number" />
      </TextField>
    </>
  );
}

function GrantForm({
  plans,
  users,
  pending,
  userQuery,
  userQueryIsValid,
  userSearchLoading,
  userSearchLoadingMore,
  hasMoreUsers,
  onSubmit,
  onUserQueryChange,
  onLoadMoreUsers,
}: {
  plans: BillingPlan[];
  users: AdminUser[];
  pending: boolean;
  userQuery: string;
  userQueryIsValid: boolean;
  userSearchLoading: boolean;
  userSearchLoadingMore: boolean;
  hasMoreUsers: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUserQueryChange: (value: string) => void;
  onLoadMoreUsers: () => void;
}) {
  const now = new Date();
  const end = new Date(now);
  end.setDate(end.getDate() + 30);
  const reference = `admin-${crypto.randomUUID()}`;

  return (
    <Card variant="default">
      <Card.Header>
        <span className="grid size-9 place-items-center rounded-xl bg-default text-muted"><UserPlus size={18} /></span>
        <div><Card.Title>发放用户订阅</Card.Title><Card.Description>管理员手动发放，引用键保证幂等</Card.Description></div>
      </Card.Header>
      <Card.Content className="gap-4">
        <div className="space-y-2">
          <TextField fullWidth><Label>查找用户</Label><Input fullWidth onChange={(event) => onUserQueryChange(event.target.value)} placeholder="邮箱前缀或显示名称" value={userQuery} /></TextField>
          <p className={`text-xs ${userQueryIsValid ? "text-muted" : "text-warning"}`}>{!userQueryIsValid ? "请输入至少 2 个字符；清空可查看最近用户。" : userSearchLoading ? "正在搜索…" : `已加载 ${users.length} 个用户，输入后会自动搜索。`}</p>
          {hasMoreUsers && <Button fullWidth isDisabled={pending || userSearchLoadingMore} onPress={onLoadMoreUsers} size="sm" variant="tertiary">{userSearchLoadingMore ? "正在加载…" : "加载更多用户"}</Button>}
        </div>
        <form className="space-y-4" onSubmit={onSubmit}>
          <Select fullWidth isRequired name="userId" placeholder="请选择用户" variant="secondary">
            <Label>用户</Label>
            <Select.Trigger><Select.Value>{({ selectedText }) => selectedText}</Select.Value><Select.Indicator /></Select.Trigger>
            <Select.Popover>
              <ListBox>
                {users.map((user) => (
                  <ListBox.Item id={String(user.id)} key={user.id} textValue={`${user.email} · ${user.displayName}`}>
                    <div className="min-w-0 flex-1"><p className="truncate text-sm font-medium">{user.email}</p><p className="truncate text-xs text-muted">#{user.id} · {user.displayName}</p></div>
                    <ListBox.ItemIndicator />
                  </ListBox.Item>
                ))}
              </ListBox>
            </Select.Popover>
          </Select>
          <Select fullWidth isRequired name="planVersionId" placeholder="请选择套餐版本" variant="secondary">
            <Label>套餐</Label>
            <Select.Trigger><Select.Value>{({ selectedText }) => selectedText}</Select.Value><Select.Indicator /></Select.Trigger>
            <Select.Popover>
              <ListBox>
                {plans.map((plan) => (
                  <ListBox.Item id={String(plan.planVersionId)} key={plan.planVersionId} textValue={`${plan.name} v${plan.versionNo}`}>
                    <div className="min-w-0 flex-1"><p className="text-sm font-medium">{plan.name} v{plan.versionNo}</p><p className="text-xs text-muted">每周 {formatQuota(plan.weeklyQuota)}</p></div>
                    <ListBox.ItemIndicator />
                  </ListBox.Item>
                ))}
              </ListBox>
            </Select.Popover>
          </Select>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField fullWidth isRequired name="startsAt"><Label>开始时间</Label><Input defaultValue={toLocalDateTime(now)} fullWidth type="datetime-local" /></TextField>
            <TextField fullWidth isRequired name="endsAt"><Label>结束时间</Label><Input defaultValue={toLocalDateTime(end)} fullWidth type="datetime-local" /></TextField>
          </div>
          <TextField fullWidth isRequired name="sourceReference"><Label>发放引用</Label><Input defaultValue={reference} fullWidth /></TextField>
          <p className="text-xs leading-5 text-muted">同一个引用重复提交相同参数时返回原订阅；参数不同则拒绝，避免重复发放。</p>
          <Button fullWidth isDisabled={pending || plans.length === 0 || users.length === 0} type="submit" variant="primary">
            {pending ? "正在发放…" : "确认发放订阅"}
          </Button>
        </form>
      </Card.Content>
    </Card>
  );
}

function PlanHistory({ versions, loading }: { versions?: BillingPlan[]; loading: boolean }) {
  return (
    <div className="rounded-xl border border-border bg-default/30 p-4">
      <p className="mb-3 text-sm font-medium">历史版本</p>
      {loading || !versions ? <p className="text-xs text-muted">正在读取版本…</p> : (
        <div className="space-y-3">
          {versions.map((version, index) => (
            <div className="flex items-center gap-3 border-b border-separator pb-3 last:border-0 last:pb-0" key={version.planVersionId}>
              <Chip color={index === 0 ? "success" : "default"} size="sm" variant="soft">v{version.versionNo}</Chip>
              <div className="min-w-0 flex-1"><p className="text-sm">{formatMoney(version.monthlyPriceMinor, version.currency)} / 月</p><p className="text-xs text-muted">每周 {formatQuota(version.weeklyQuota)}</p></div>
              <span className="text-xs text-muted">ID {version.planVersionId}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function RecentSubscriptions({ subscriptions, plans, users }: {
  subscriptions: BillingSubscription[];
  plans: BillingPlan[];
  users: AdminUser[];
}) {
  return (
    <Card variant="default">
      <Card.Header>
        <Clock3 className="text-muted" size={19} />
        <div><Card.Title>最近订阅</Card.Title><Card.Description>最多显示最近 100 条管理员发放或购买记录</Card.Description></div>
      </Card.Header>
      <Card.Content className="pt-1">
        {subscriptions.length === 0 ? <p className="py-6 text-center text-sm text-muted">暂无订阅记录。</p> : (
          <div className="divide-y divide-separator">
            {subscriptions.map((subscription) => {
              const user = users.find((item) => item.id === subscription.userId);
              const plan = plans.find((item) => item.planVersionId === subscription.planVersionId);
              return (
                <div className="grid gap-2 py-4 text-sm md:grid-cols-[minmax(0,1.2fr)_minmax(0,.8fr)_minmax(0,1fr)_auto] md:items-center" key={subscription.subscriptionId}>
                  <div className="min-w-0"><p className="truncate font-medium">{user?.email ?? `用户 #${subscription.userId}`}</p><p className="truncate text-xs text-muted">{subscription.sourceReference ?? subscription.subscriptionId}</p></div>
                  <div><p>{plan ? `${plan.name} v${plan.versionNo}` : `版本 #${subscription.planVersionId}`}</p><p className="text-xs text-muted">{subscription.source === "ADMIN_GRANT" ? "管理员发放" : "用户购买"}</p></div>
                  <div className="text-xs text-muted"><p>{formatDate(subscription.startsAt)}</p><p>至 {formatDate(subscription.endsAt)}</p></div>
                  <Chip color={isCurrentlyActive(subscription) ? "success" : "default"} size="sm" variant="soft">{isCurrentlyActive(subscription) ? "生效中" : subscription.status}</Chip>
                </div>
              );
            })}
          </div>
        )}
      </Card.Content>
    </Card>
  );
}

function RecentOrders({ orders, users }: { orders: PurchaseOrder[]; users: AdminUser[] }) {
  return (
    <Card variant="default">
      <Card.Header>
        <ShoppingBag className="text-muted" size={19} />
        <div><Card.Title>最近订单</Card.Title><Card.Description>支付状态和订阅发放结果，仅用于运营观测</Card.Description></div>
      </Card.Header>
      <Card.Content className="pt-1">
        {orders.length === 0 ? <p className="py-6 text-center text-sm text-muted">暂无购买订单。</p> : (
          <div className="divide-y divide-separator">
            {orders.map((order) => {
              const user = users.find((item) => item.id === order.userId);
              return (
                <div className="grid gap-2 py-4 text-sm md:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,.8fr)_auto] md:items-center" key={order.orderNo}>
                  <div className="min-w-0"><p className="truncate font-medium">{user?.email ?? `用户 #${order.userId}`}</p><p className="truncate text-xs text-muted">{order.orderNo}</p></div>
                  <div><p>{order.planName}</p><p className="text-xs text-muted">版本 #{order.planVersionId}</p></div>
                  <div><p>{formatMoney(order.amountMinor, order.currency)}</p><p className="text-xs text-muted">{formatDate(order.createdAt)}</p></div>
                  <OrderStatus status={order.status} />
                </div>
              );
            })}
          </div>
        )}
      </Card.Content>
    </Card>
  );
}

function OrderStatus({ status }: { status: PurchaseOrder["status"] }) {
  const view = {
    PENDING_PAYMENT: { label: "待支付", color: "warning" as const },
    FULFILLED: { label: "已完成", color: "success" as const },
    CANCELED: { label: "已取消", color: "default" as const },
    EXPIRED: { label: "已过期", color: "danger" as const },
  }[status];
  return <Chip color={view.color} size="sm" variant="soft">{view.label}</Chip>;
}

function Summary({ icon: Icon, label, value }: { icon: typeof WalletCards; label: string; value: number }) {
  return <Card variant="default"><Card.Content className="flex-row items-center gap-3"><span className="grid size-9 place-items-center rounded-xl bg-default text-muted"><Icon size={18} /></span><div><p className="text-xs text-muted">{label}</p><p className="text-xl font-semibold">{value}</p></div></Card.Content></Card>;
}

function Info({ label, value }: { label: string; value: string }) {
  return <div className="rounded-xl bg-default/40 px-3 py-2.5"><p className="text-xs text-muted">{label}</p><p className="mt-1 text-sm">{value}</p></div>;
}

function isCurrentlyActive(subscription: BillingSubscription): boolean {
  const now = Date.now();
  return subscription.status === "ACTIVE" && new Date(subscription.startsAt).getTime() <= now && new Date(subscription.endsAt).getTime() > now;
}

function formatMoney(minor: number, currency: string): string {
  return new Intl.NumberFormat("zh-CN", { style: "currency", currency }).format(minor / 100);
}

function formatQuota(value: number): string {
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 6 }).format(value);
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function toLocalDateTime(date: Date): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function localDateTimeToIso(value: string): string {
  return new Date(value).toISOString();
}

function textValue(form: FormData, name: string): string {
  return String(form.get(name) ?? "").trim();
}

function numberValue(form: FormData, name: string): number {
  return Number(textValue(form, name));
}

function minorUnits(form: FormData, name: string): number {
  return Math.round(numberValue(form, name) * 100);
}

function message(reason: unknown): string {
  return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试";
}
