import { Button, Card, Chip } from "@heroui/react";
import {
  ArrowLeft,
  CheckCircle2,
  Clock3,
  CreditCard,
  RefreshCw,
  ReceiptText,
  ShieldAlert,
  XCircle,
} from "lucide-react";
import { useEffect, useState } from "react";

import { ApiClientError } from "../../api/auth";
import {
  cancelPurchaseOrder,
  completeMockPayment,
  getPaymentCapabilities,
  getPurchaseOrder,
  listPurchaseOrders,
  type PaymentCapabilities,
  type PurchaseOrder,
} from "../../api/billing";

interface PurchaseOrdersPageProps {
  orderNo?: string;
}

export function PurchaseOrdersPage({ orderNo }: PurchaseOrdersPageProps) {
  return orderNo ? <OrderCheckout orderNo={orderNo} /> : <OrderList />;
}

function OrderList() {
  const [orders, setOrders] = useState<PurchaseOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setOrders(await listPurchaseOrders());
    } catch (reason) {
      setError(message(reason));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-1 text-sm text-muted">购买与续费</p>
          <h1 className="text-2xl font-semibold tracking-tight">订单记录</h1>
          <p className="mt-2 text-sm text-muted">查看套餐订单、支付状态和对应订阅。</p>
        </div>
        <Button isDisabled={loading} onPress={() => void load()} variant="secondary">
          <RefreshCw size={16} /> 刷新
        </Button>
      </header>

      {error && <Feedback error={error} />}

      <Card variant="default">
        <Card.Header>
          <ReceiptText className="text-muted" size={19} />
          <div><Card.Title>我的订单</Card.Title><Card.Description>最多显示最近 100 条订单</Card.Description></div>
        </Card.Header>
        <Card.Content className="pt-1">
          {loading ? (
            <p className="py-12 text-center text-sm text-muted">正在读取订单…</p>
          ) : orders.length === 0 ? (
            <div className="py-12 text-center">
              <p className="text-sm font-medium">还没有订单</p>
              <p className="mt-1 text-xs text-muted">选择套餐后会先创建待支付订单。</p>
              <Button className="mt-4" onPress={() => window.location.assign("/console/plans")} size="sm" variant="primary">查看套餐</Button>
            </div>
          ) : (
            <div className="divide-y divide-separator">
              {orders.map((order) => (
                <div className="grid gap-3 py-4 text-sm md:grid-cols-[minmax(0,1.2fr)_minmax(0,.8fr)_minmax(0,.8fr)_auto] md:items-center" key={order.orderNo}>
                  <div className="min-w-0">
                    <p className="truncate font-medium">{order.planName}</p>
                    <p className="mt-1 truncate text-xs text-muted">{order.orderNo}</p>
                  </div>
                  <div><p className="font-medium">{formatMoney(order.amountMinor, order.currency)}</p><p className="text-xs text-muted">{formatDate(order.createdAt)}</p></div>
                  <OrderStatusChip status={order.status} />
                  <Button onPress={() => window.location.assign(`/console/orders/${order.orderNo}`)} size="sm" variant={order.status === "PENDING_PAYMENT" ? "primary" : "secondary"}>
                    {order.status === "PENDING_PAYMENT" ? "继续支付" : "查看详情"}
                  </Button>
                </div>
              ))}
            </div>
          )}
        </Card.Content>
      </Card>
    </div>
  );
}

function OrderCheckout({ orderNo }: { orderNo: string }) {
  const [order, setOrder] = useState<PurchaseOrder | null>(null);
  const [capabilities, setCapabilities] = useState<PaymentCapabilities | null>(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState<"pay" | "cancel" | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [orderData, capabilityData] = await Promise.all([
        getPurchaseOrder(orderNo),
        getPaymentCapabilities(),
      ]);
      setOrder(orderData);
      setCapabilities(capabilityData);
    } catch (reason) {
      setError(message(reason));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [orderNo]);

  async function pay() {
    setPending("pay");
    setError(null);
    try {
      setOrder(await completeMockPayment(orderNo));
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  async function cancel() {
    setPending("cancel");
    setError(null);
    try {
      setOrder(await cancelPurchaseOrder(orderNo));
    } catch (reason) {
      setError(message(reason));
    } finally {
      setPending(null);
    }
  }

  if (loading) return <div className="grid min-h-[55vh] place-items-center text-sm text-muted">正在读取订单…</div>;

  if (!order) {
    return <div className="space-y-4"><Button onPress={() => window.location.assign("/console/orders")} size="sm" variant="ghost"><ArrowLeft size={15} /> 返回订单</Button>{error && <Feedback error={error} />}</div>;
  }

  const mockAvailable = order.mockPaymentEnabled && capabilities?.availableMethods.includes("MOCK");

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Button onPress={() => window.location.assign("/console/orders")} size="sm" variant="ghost"><ArrowLeft size={15} /> 返回订单</Button>
      {error && <Feedback error={error} />}

      <Card variant="default">
        <Card.Header className="flex-row items-start justify-between gap-4">
          <div>
            <Card.Title>{order.status === "PENDING_PAYMENT" ? "确认订单" : "订单详情"}</Card.Title>
            <Card.Description className="mt-1">{order.orderNo}</Card.Description>
          </div>
          <OrderStatusChip status={order.status} />
        </Card.Header>
        <Card.Content className="gap-5">
          <div className="rounded-xl bg-default/40 p-4">
            <div className="flex items-start justify-between gap-4">
              <div><p className="font-medium">{order.planName}</p><p className="mt-1 text-xs text-muted">{order.planCode} · 套餐版本 #{order.planVersionId}</p></div>
              <strong className="text-xl font-semibold">{formatMoney(order.amountMinor, order.currency)}</strong>
            </div>
          </div>

          <dl className="grid gap-4 text-sm sm:grid-cols-2">
            <OrderField label="创建时间" value={formatDate(order.createdAt)} />
            <OrderField label="支付截止时间" value={formatDate(order.expiresAt)} />
            {order.paidAt && <OrderField label="支付完成时间" value={formatDate(order.paidAt)} />}
            {order.subscriptionId && <OrderField label="订阅编号" value={order.subscriptionId} />}
          </dl>

          {order.status === "PENDING_PAYMENT" && (
            <>
              {mockAvailable ? (
                <div className="rounded-xl border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-warning">
                  <div className="flex items-start gap-3"><ShieldAlert className="mt-0.5 shrink-0" size={17} /><p><strong>开发环境测试支付</strong><br /><span className="text-xs">不会调用支付宝、微信或银行卡，也不会产生真实扣款。确认后会直接模拟支付成功并发放订阅。</span></p></div>
                </div>
              ) : (
                <div className="rounded-xl bg-default px-4 py-3 text-sm text-muted">当前环境没有可用支付方式，订单仍会保留到截止时间。</div>
              )}
              <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                <Button isDisabled={pending !== null} onPress={() => void cancel()} variant="tertiary"><XCircle size={16} /> {pending === "cancel" ? "正在取消…" : "取消订单"}</Button>
                {mockAvailable && <Button isDisabled={pending !== null} onPress={() => void pay()} variant="primary"><CreditCard size={16} /> {pending === "pay" ? "正在确认…" : "使用测试支付"}</Button>}
              </div>
            </>
          )}

          {order.status === "FULFILLED" && (
            <Result icon={CheckCircle2} title="支付与订阅发放已完成" description="本次操作具备幂等保护，重复确认不会重复创建订阅。">
              <Button onPress={() => window.location.assign("/console")} variant="primary">查看套餐概览</Button>
            </Result>
          )}
          {order.status === "CANCELED" && <Result icon={XCircle} title="订单已取消" description="该订单不会再进入支付或发放订阅。"><Button onPress={() => window.location.assign("/console/plans")} variant="secondary">重新选择套餐</Button></Result>}
          {order.status === "EXPIRED" && <Result icon={Clock3} title="订单已过期" description="价格快照已失效，请重新创建订单。"><Button onPress={() => window.location.assign("/console/plans")} variant="primary">重新下单</Button></Result>}
        </Card.Content>
      </Card>
    </div>
  );
}

function OrderStatusChip({ status }: { status: PurchaseOrder["status"] }) {
  const content = {
    PENDING_PAYMENT: { label: "待支付", color: "warning" as const },
    FULFILLED: { label: "已完成", color: "success" as const },
    CANCELED: { label: "已取消", color: "default" as const },
    EXPIRED: { label: "已过期", color: "danger" as const },
  }[status];
  return <Chip color={content.color} size="sm" variant="soft">{content.label}</Chip>;
}

function OrderField({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs text-muted">{label}</dt><dd className="mt-1 break-all">{value}</dd></div>;
}

function Result({ icon: Icon, title, description, children }: { icon: typeof CheckCircle2; title: string; description: string; children: React.ReactNode }) {
  return <div className="flex flex-col items-center rounded-xl bg-default/40 px-5 py-7 text-center"><Icon className="text-muted" size={28} /><p className="mt-3 font-medium">{title}</p><p className="mt-1 max-w-md text-xs leading-5 text-muted">{description}</p><div className="mt-4">{children}</div></div>;
}

function Feedback({ error }: { error: string }) {
  return <div className="rounded-xl bg-danger-soft px-4 py-3 text-sm text-danger" role="alert">{error}</div>;
}

function formatMoney(minor: number, currency: string): string {
  return new Intl.NumberFormat("zh-CN", { style: "currency", currency, maximumFractionDigits: 2 }).format(minor / 100);
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function message(reason: unknown): string {
  return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试";
}
