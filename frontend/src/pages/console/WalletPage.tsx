import { Button, Card, Chip, Input, Label, ListBox, Select, TextField } from "@heroui/react";
import { ArrowDownLeft, ArrowUpRight, CreditCard, Landmark, Plus, RefreshCw } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { ApiClientError } from "../../api/auth";
import {
  cancelWalletTopup,
  completeMockTopup,
  createWalletTopup,
  getWalletOverview,
  getPaymentCapabilities,
  type PaymentCapabilities,
  type WalletOverview,
  type WalletTopupOrder,
} from "../../api/billing";

export function WalletPage() {
  const [wallet, setWallet] = useState<WalletOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [pendingOrder, setPendingOrder] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [capabilities, setCapabilities] = useState<PaymentCapabilities | null>(null);
  const creating = useRef(false);
  const operationKeys = useRef(new Map<string, string>());
  const mockAvailable = capabilities?.availableMethods.includes("MOCK") ?? false;

  async function load() {
    setLoading(true); setError(null);
    try {
      const [overview, methods] = await Promise.all([getWalletOverview(), getPaymentCapabilities()]);
      setWallet(overview);
      setCapabilities(methods);
    }
    catch (reason) { setError(message(reason)); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  async function create(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (creating.current || !mockAvailable) return;
    setError(null); setNotice(null);
    const element = event.currentTarget;
    const form = new FormData(element);
    const amount = Number(form.get("amount"));
    const currency = String(form.get("currency") || "CNY");
    if (!Number.isFinite(amount) || amount <= 0) { setError("请输入大于 0 的充值金额"); return; }
    const amountMinor = Math.round(amount * 100);
    if (!Number.isSafeInteger(amountMinor) || amountMinor <= 0) { setError("充值金额超出有效范围"); return; }
    const signature = JSON.stringify({ amountMinor, currency });
    const key = operationKeys.current.get(signature) ?? crypto.randomUUID();
    operationKeys.current.set(signature, key);
    creating.current = true;
    setPendingOrder("create");
    try {
      const created = await createWalletTopup(amountMinor, currency, key);
      operationKeys.current.delete(signature);
      element.reset();
      setNotice(`充值订单 ${created.orderNo} 已创建`);
      await load();
    } catch (reason) {
      if (reason instanceof ApiClientError && reason.status >= 400 && reason.status < 500
          && reason.status !== 408 && reason.status !== 429) operationKeys.current.delete(signature);
      setError(message(reason));
    } finally { creating.current = false; setPendingOrder(null); }
  }

  async function update(order: WalletTopupOrder, action: "pay" | "cancel") {
    setPendingOrder(order.orderNo); setError(null);
    try {
      if (action === "pay") await completeMockTopup(order.orderNo);
      else await cancelWalletTopup(order.orderNo);
      await load();
    } catch (reason) { setError(message(reason)); }
    finally { setPendingOrder(null); }
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div><p className="mb-1 text-sm text-muted">余额与充值</p><h1 className="text-2xl font-semibold tracking-tight">钱包管理</h1><p className="mt-2 text-sm text-muted">余额以不可变流水和数据库事务为准，可用于支付同币种套餐订单。</p></div><Button isDisabled={loading} onPress={() => void load()} variant="secondary"><RefreshCw size={16} />刷新</Button></header>
      {notice && <div className="rounded-xl bg-success-soft px-4 py-3 text-sm text-success">{notice}</div>}
      {error && <div className="rounded-xl bg-danger-soft px-4 py-3 text-sm text-danger">{error}</div>}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {wallet?.accounts.map((account) => <Card key={account.accountId} variant="default"><Card.Content className="gap-3"><span className="grid size-10 place-items-center rounded-xl bg-default text-muted"><Landmark size={19} /></span><div><p className="text-sm text-muted">{account.currency} 可用余额</p><strong className="mt-1 block text-2xl font-semibold tabular-nums">{money(account.availableMinor, account.currency)}</strong></div><p className="text-xs text-muted">余额版本 #{account.version}</p></Card.Content></Card>)}
      </section>

      <section className="grid items-start gap-6 xl:grid-cols-[minmax(320px,.7fr)_minmax(0,1.3fr)]">
        <Card variant="default">
          <Card.Header><Plus className="text-muted" size={19} /><div><Card.Title>钱包充值</Card.Title><Card.Description>{mockAvailable ? "开发环境模拟到账，不产生真实扣款" : "当前环境暂未开放充值渠道"}</Card.Description></div></Card.Header>
          <Card.Content>
            <form className="space-y-4" onSubmit={(event) => void create(event)}>
              <TextField fullWidth isRequired name="amount"><Label>充值金额</Label><Input fullWidth min="0.01" placeholder="例如 100.00" step="0.01" type="number" /></TextField>
              <Select defaultSelectedKey="CNY" fullWidth isRequired name="currency" variant="secondary"><Label>币种</Label><Select.Trigger><Select.Value /><Select.Indicator /></Select.Trigger><Select.Popover><ListBox><ListBox.Item id="CNY">CNY</ListBox.Item><ListBox.Item id="USD">USD</ListBox.Item></ListBox></Select.Popover></Select>
              <Button fullWidth isDisabled={pendingOrder !== null || !mockAvailable} type="submit" variant="primary"><CreditCard size={16} />{pendingOrder === "create" ? "正在创建…" : "创建充值订单"}</Button>
            </form>
          </Card.Content>
        </Card>

        <Card variant="default">
          <Card.Header><Card.Title>充值订单</Card.Title><Card.Description>待支付订单 30 分钟后自动过期</Card.Description></Card.Header>
          <Card.Content className="gap-0 pt-1">
            {loading ? <Empty text="正在读取充值订单…" /> : !wallet?.topupOrders.length ? <Empty text="暂无充值订单" /> : wallet.topupOrders.map((order) => (
              <div className="grid gap-3 border-b border-separator py-4 last:border-0 md:grid-cols-[minmax(0,1fr)_auto_auto] md:items-center" key={order.orderNo}>
                <div><p className="text-sm font-medium">{money(order.amountMinor, order.currency)}</p><p className="text-xs text-muted">{order.orderNo} · {formatDate(order.createdAt)}</p></div>
                <TopupStatus status={order.status} />
                {order.status === "PENDING_PAYMENT" ? <div className="flex gap-2"><Button isDisabled={pendingOrder !== null} onPress={() => void update(order, "cancel")} size="sm" variant="tertiary">取消</Button>{mockAvailable && order.mockPaymentEnabled && <Button isDisabled={pendingOrder !== null} onPress={() => void update(order, "pay")} size="sm" variant="primary">模拟支付</Button>}</div> : <span className="text-xs text-muted">{order.paidAt ? formatDate(order.paidAt) : "—"}</span>}
              </div>
            ))}
          </Card.Content>
        </Card>
      </section>

      <Card variant="default">
        <Card.Header><Card.Title>余额流水</Card.Title><Card.Description>流水创建后不可编辑或删除</Card.Description></Card.Header>
        <Card.Content className="gap-0 pt-1">
          {!wallet?.ledger.length ? <Empty text="暂无余额流水" /> : wallet.ledger.map((entry) => (
            <div className="grid gap-3 border-b border-separator py-4 last:border-0 md:grid-cols-[auto_minmax(0,1fr)_auto] md:items-center" key={entry.id}>
              <span className={`grid size-9 place-items-center rounded-xl ${entry.amountDelta > 0 ? "bg-success-soft text-success" : "bg-default text-muted"}`}>{entry.amountDelta > 0 ? <ArrowDownLeft size={17} /> : <ArrowUpRight size={17} />}</span>
              <div><p className="text-sm font-medium">{entry.description || entry.entryType}</p><p className="text-xs text-muted">{entry.referenceId} · {formatDate(entry.createdAt)}</p></div>
              <div className="text-right"><p className={`font-medium tabular-nums ${entry.amountDelta > 0 ? "text-success" : ""}`}>{entry.amountDelta > 0 ? "+" : ""}{money(entry.amountDelta, entry.currency)}</p><p className="text-xs text-muted">余额 {money(entry.balanceAfter, entry.currency)}</p></div>
            </div>
          ))}
        </Card.Content>
      </Card>
    </div>
  );
}

function TopupStatus({ status }: { status: WalletTopupOrder["status"] }) { const config = { PENDING_PAYMENT: ["待支付", "warning"], PAID: ["已到账", "success"], CANCELED: ["已取消", "default"], EXPIRED: ["已过期", "danger"] }[status] as [string, "warning" | "success" | "default" | "danger"]; return <Chip color={config[1]} size="sm" variant="soft">{config[0]}</Chip>; }
function Empty({ text }: { text: string }) { return <p className="py-10 text-center text-sm text-muted">{text}</p>; }
function money(minor: number, currency: string) { return new Intl.NumberFormat("zh-CN", { style: "currency", currency, maximumFractionDigits: 2 }).format(minor / 100); }
function formatDate(value: string) { return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); }
function message(reason: unknown) { return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试"; }
