import { Button, Card, Chip, Input, Label, ListBox, Select, TextArea, TextField } from "@heroui/react";
import { Landmark, RefreshCw, SlidersHorizontal } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { ApiClientError } from "../../api/auth";
import { adjustAdminWallet, getAdminWallet, type WalletOverview } from "../../api/billing";
import { useAdminUserSearch } from "../../features/admin/useAdminUserSearch";

export function AdminWalletsPage() {
  const {
    query,
    setQuery,
    users,
    loading,
    loadingMore,
    error: searchError,
    hasMore,
    queryIsValid,
    reload,
    loadMore,
  } = useAdminUserSearch();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [wallet, setWallet] = useState<WalletOverview | null>(null);
  const [walletLoading, setWalletLoading] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selected = useMemo(
    () => users.find((user) => user.id === selectedId) ?? null,
    [selectedId, users],
  );

  useEffect(() => {
    setSelectedId((current) => users.some((user) => user.id === current) ? current : users[0]?.id ?? null);
  }, [users]);

  useEffect(() => {
    if (selectedId == null) {
      setWallet(null);
      setWalletLoading(false);
      return;
    }
    let active = true;
    setWalletLoading(true);
    setError(null);
    void getAdminWallet(selectedId)
      .then((data) => { if (active) setWallet(data); })
      .catch((reason) => { if (active) setError(message(reason)); })
      .finally(() => { if (active) setWalletLoading(false); });
    return () => { active = false; };
  }, [selectedId]);

  async function refresh() {
    void reload();
    if (!selected) return;
    setWalletLoading(true); setError(null);
    try { setWallet(await getAdminWallet(selected.id)); }
    catch (reason) { setError(message(reason)); }
    finally { setWalletLoading(false); }
  }

  async function adjust(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selected) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    const amount = Number(data.get("amount"));
    if (!Number.isFinite(amount) || amount === 0) { setError("调整金额不能为 0"); return; }
    setPending(true); setError(null);
    try {
      await adjustAdminWallet({ userId: selected.id, amountDelta: Math.round(amount * 100), currency: String(data.get("currency") || "CNY"), reason: String(data.get("reason") || "") }, crypto.randomUUID());
      setWallet(await getAdminWallet(selected.id)); form.reset();
    } catch (reason) { setError(message(reason)); }
    finally { setPending(false); }
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div><p className="mb-1 text-sm text-muted">资金运营</p><h1 className="text-2xl font-semibold tracking-tight">用户钱包</h1><p className="mt-2 text-sm text-muted">查询余额与流水，并通过带原因和幂等键的调整单进行人工增减。</p></div><Button isDisabled={loading || walletLoading} onPress={() => void refresh()} variant="secondary"><RefreshCw size={16} />刷新</Button></header>
      {(error || searchError) && <div className="rounded-xl bg-danger-soft px-4 py-3 text-sm text-danger">{error ?? searchError}</div>}
      <div className="max-w-xl space-y-2"><TextField fullWidth><Label>查找用户</Label><Input fullWidth onChange={(event) => setQuery(event.target.value)} placeholder="邮箱或显示名称" value={query} /></TextField><p className={`text-xs ${queryIsValid ? "text-muted" : "text-warning"}`}>{!queryIsValid ? "请输入至少 2 个字符；清空可查看最近用户。" : loading ? "正在搜索…" : `已加载 ${users.length} 个用户，输入后会自动搜索。`}</p></div>

      <section className="grid items-start gap-6 xl:grid-cols-[minmax(280px,.55fr)_minmax(0,1.45fr)]">
        <Card variant="default"><Card.Header><Card.Title>用户</Card.Title><Card.Description>每页 20 个，选择钱包所属账号</Card.Description></Card.Header><Card.Content className="gap-0 pt-1">{loading ? <p className="py-10 text-center text-sm text-muted">正在读取用户…</p> : users.length === 0 ? <p className="py-10 text-center text-sm text-muted">没有匹配的用户</p> : users.map((user) => <button className={`w-full border-b border-separator px-2 py-3 text-left last:border-0 ${selected?.id === user.id ? "bg-default/50" : "hover:bg-default/30"}`} key={user.id} onClick={() => setSelectedId(user.id)} type="button"><p className="truncate text-sm font-medium">{user.displayName}</p><p className="truncate text-xs text-muted">{user.email} · #{user.id}</p></button>)}{hasMore && <Button className="mt-4" fullWidth isDisabled={loadingMore} onPress={() => void loadMore()} variant="tertiary">{loadingMore ? "正在加载…" : "加载更多用户"}</Button>}</Card.Content></Card>

        <div className="space-y-6">
          <section className="grid gap-4 sm:grid-cols-2">{walletLoading ? <Card variant="default"><Card.Content className="py-8 text-center text-sm text-muted">正在读取钱包…</Card.Content></Card> : wallet?.accounts.map((account) => <Card key={account.accountId} variant="default"><Card.Content className="gap-3"><Landmark className="text-muted" size={19} /><div><p className="text-sm text-muted">{account.currency} 余额</p><strong className="mt-1 block text-2xl font-semibold">{money(account.availableMinor, account.currency)}</strong></div></Card.Content></Card>)}</section>
          <Card variant="default"><Card.Header><SlidersHorizontal className="text-muted" size={19} /><div><Card.Title>管理员调整</Card.Title><Card.Description>正数增加、负数扣减；余额不能为负</Card.Description></div></Card.Header><Card.Content><form className="grid gap-4 sm:grid-cols-2" onSubmit={(event) => void adjust(event)}><TextField fullWidth isRequired name="amount"><Label>调整金额</Label><Input fullWidth placeholder="例如 100 或 -20" step="0.01" type="number" /></TextField><Select defaultSelectedKey="CNY" fullWidth isRequired name="currency" variant="secondary"><Label>币种</Label><Select.Trigger><Select.Value /><Select.Indicator /></Select.Trigger><Select.Popover><ListBox><ListBox.Item id="CNY">CNY</ListBox.Item><ListBox.Item id="USD">USD</ListBox.Item></ListBox></Select.Popover></Select><TextField className="sm:col-span-2" fullWidth isRequired name="reason"><Label>调整原因</Label><TextArea fullWidth placeholder="请输入审计原因" rows={2} /></TextField><Button className="sm:col-span-2" fullWidth isDisabled={!selected || pending || loading || walletLoading} type="submit" variant="primary">{pending ? "正在提交…" : "提交调整"}</Button></form></Card.Content></Card>
          <Card variant="default"><Card.Header><Card.Title>最近流水</Card.Title><Card.Description>{selected?.email ?? "请选择用户"}</Card.Description></Card.Header><Card.Content className="gap-0 pt-1">{!wallet?.ledger.length ? <p className="py-10 text-center text-sm text-muted">暂无钱包流水</p> : wallet.ledger.map((entry) => <div className="flex items-center justify-between gap-4 border-b border-separator py-4 last:border-0" key={entry.id}><div><p className="text-sm font-medium">{entry.description || entry.entryType}</p><p className="text-xs text-muted">{entry.referenceId} · {formatDate(entry.createdAt)}</p></div><div className="text-right"><p className={`font-medium ${entry.amountDelta > 0 ? "text-success" : ""}`}>{entry.amountDelta > 0 ? "+" : ""}{money(entry.amountDelta, entry.currency)}</p><Chip size="sm" variant="soft">余额 {money(entry.balanceAfter, entry.currency)}</Chip></div></div>)}</Card.Content></Card>
        </div>
      </section>
    </div>
  );
}

function money(minor: number, currency: string) { return new Intl.NumberFormat("zh-CN", { style: "currency", currency, maximumFractionDigits: 2 }).format(minor / 100); }
function formatDate(value: string) { return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); }
function message(reason: unknown) { return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试"; }
