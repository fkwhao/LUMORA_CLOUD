import { Button, Card, Chip } from "@heroui/react";
import { ArrowLeft, Check, RefreshCw, ShieldCheck, ShoppingCart } from "lucide-react";
import { useEffect, useState } from "react";

import { ApiClientError } from "../../api/auth";
import {
  getBillingOverview,
  listPublishedPlans,
  createPurchaseOrder,
  type BillingOverview,
  type BillingPlan,
} from "../../api/billing";

export function PlansPage() {
  const [plans, setPlans] = useState<BillingPlan[]>([]);
  const [overview, setOverview] = useState<BillingOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [pendingPlanId, setPendingPlanId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    const [plansResult, overviewResult] = await Promise.allSettled([listPublishedPlans(), getBillingOverview()]);
    if (plansResult.status === "fulfilled") {
      setPlans(plansResult.value);
    }
    if (overviewResult.status === "fulfilled") {
      setOverview(overviewResult.value);
    }
    const failure = plansResult.status === "rejected"
      ? plansResult.reason
      : overviewResult.status === "rejected" ? overviewResult.reason : null;
    if (failure) {
      setError(message(failure));
    }
    setLoading(false);
  }

  useEffect(() => {
    void load();
  }, []);

  async function purchase(plan: BillingPlan) {
    const storageKey = `lumora.purchase.${plan.planVersionId}`;
    const idempotencyKey = window.sessionStorage.getItem(storageKey) ?? crypto.randomUUID();
    window.sessionStorage.setItem(storageKey, idempotencyKey);
    setPendingPlanId(plan.planId);
    setError(null);
    try {
      const order = await createPurchaseOrder(plan.planVersionId, idempotencyKey);
      window.sessionStorage.removeItem(storageKey);
      window.location.assign(`/console/orders/${order.orderNo}`);
    } catch (reason) {
      setError(message(reason));
      setPendingPlanId(null);
    }
  }

  return (
    <div className="space-y-6">
      <Button onPress={() => window.location.assign("/console")} size="sm" variant="ghost">
        <ArrowLeft size={15} /> 返回概览
      </Button>

      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">可用套餐</h1>
          <p className="mt-2 text-sm text-muted">以下内容来自 Billing Service 当前发布的最新套餐版本。</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Chip color="success" variant="soft"><ShieldCheck size={14} /> 购买只在网页端完成</Chip>
          <Button isDisabled={loading} onPress={() => void load()} size="sm" variant="secondary"><RefreshCw size={15} /> 刷新</Button>
        </div>
      </header>

      {error && <div className="rounded-xl bg-danger-soft px-4 py-3 text-sm text-danger" role="alert">{error}</div>}

      {loading ? (
        <div className="grid min-h-[45vh] place-items-center text-sm text-muted">正在读取套餐…</div>
      ) : plans.length === 0 ? (
        <Card variant="default"><Card.Content className="py-12 text-center text-sm text-muted">当前没有已发布套餐。</Card.Content></Card>
      ) : (
        <section className="grid gap-5 lg:grid-cols-3">
          {plans.map((plan) => {
            const current = overview?.hasActiveSubscription && overview.plan?.planId === plan.planId;
            return (
              <Card className="min-h-[390px]" key={plan.planId} variant={current ? "secondary" : "default"}>
                <Card.Header className="gap-3">
                  <div className="flex items-center justify-between gap-3">
                    <Card.Title className="text-lg">{plan.name}</Card.Title>
                    {current && <Chip color="accent" size="sm" variant="soft">当前套餐</Chip>}
                  </div>
                  <Card.Description>{plan.description || "暂无套餐说明"}</Card.Description>
                </Card.Header>

                <Card.Content className="gap-5 py-3">
                  <div><strong className="text-3xl font-semibold">{formatMoney(plan.monthlyPriceMinor, plan.currency)}</strong><span className="ml-1 text-sm text-muted">/月</span></div>
                  <ul className="space-y-3 text-sm text-muted">
                    <li className="flex items-center gap-2"><Check className="text-success" size={16} /> 每周 {formatQuota(plan.weeklyQuota)} 套餐额度</li>
                    <li className="flex items-center gap-2"><Check className="text-success" size={16} /> Lumora 云端模型目录</li>
                    <li className="flex items-center gap-2"><Check className="text-success" size={16} /> 每七天自动创建新额度周期</li>
                    <li className="flex items-center gap-2"><Check className="text-success" size={16} /> 当前发布版本 v{plan.versionNo}</li>
                  </ul>
                </Card.Content>

                <Card.Footer className="mt-auto">
                  <Button
                    fullWidth
                    isDisabled={pendingPlanId !== null}
                    onPress={() => void purchase(plan)}
                    type="button"
                    variant={current ? "secondary" : "primary"}
                  >
                    <ShoppingCart size={16} /> {pendingPlanId === plan.planId ? "正在创建订单…" : current ? "续费 30 天" : "购买套餐"}
                  </Button>
                </Card.Footer>
              </Card>
            );
          })}
        </section>
      )}

      <p className="text-center text-xs text-muted">下单时锁定当前套餐版本和金额；续费订阅会自动排在现有有效期之后。</p>
    </div>
  );
}

function formatMoney(minor: number, currency: string): string {
  return new Intl.NumberFormat("zh-CN", { style: "currency", currency, maximumFractionDigits: 2 }).format(minor / 100);
}

function formatQuota(value: number): string {
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 6 }).format(value);
}

function message(reason: unknown): string {
  return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试";
}
