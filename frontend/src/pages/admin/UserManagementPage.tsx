import { Button, Card, Checkbox, Chip, Input, Label, TextField } from "@heroui/react";
import { RefreshCw, ShieldCheck, UserRoundCog } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { ApiClientError } from "../../api/auth";
import {
  listAdminRoles,
  listAdminUserSessions,
  revokeAdminUserSession,
  updateAdminUserRoles,
  updateAdminUserStatus,
  type AdminRole,
  type AdminUser,
  type AdminUserSession,
} from "../../api/users";
import { useAdminUserSearch } from "../../features/admin/useAdminUserSearch";

export function UserManagementPage() {
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
    updateUser,
  } = useAdminUserSearch();
  const [roles, setRoles] = useState<AdminRole[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [sessions, setSessions] = useState<AdminUserSession[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selected = useMemo(
    () => users.find((user) => user.id === selectedId) ?? null,
    [selectedId, users],
  );

  useEffect(() => {
    let active = true;
    void listAdminRoles()
      .then((roleData) => { if (active) setRoles(roleData); })
      .catch((reason) => { if (active) setError(message(reason)); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    setSelectedId((current) => users.some((user) => user.id === current) ? current : users[0]?.id ?? null);
  }, [users]);

  useEffect(() => {
    if (selectedId == null) {
      setSessions([]);
      setSessionsLoading(false);
      return;
    }
    let active = true;
    setSessionsLoading(true);
    setError(null);
    void listAdminUserSessions(selectedId)
      .then((data) => { if (active) setSessions(data); })
      .catch((reason) => { if (active) setError(message(reason)); })
      .finally(() => { if (active) setSessionsLoading(false); });
    return () => { active = false; };
  }, [selectedId]);

  async function toggleRole(role: string, enabled: boolean) {
    if (!selected) return;
    const next = enabled
      ? Array.from(new Set([...selected.roles, role]))
      : selected.roles.filter((value) => value !== role);
    await mutate(async () => updateAdminUserRoles(selected.id, next));
  }

  async function mutate(operation: () => Promise<AdminUser>) {
    setPending(true); setError(null);
    try {
      const updated = await operation();
      updateUser(updated);
      setSessions(await listAdminUserSessions(updated.id));
    } catch (reason) { setError(message(reason)); }
    finally { setPending(false); }
  }

  async function revoke(sessionId: string) {
    if (!selected) return;
    setPending(true); setError(null);
    try {
      const updated = await revokeAdminUserSession(selected.id, sessionId);
      setSessions((current) => current.map((item) => item.id === updated.id ? updated : item));
      updateUser({ ...selected, activeSessions: Math.max(0, selected.activeSessions - 1) });
    } catch (reason) { setError(message(reason)); }
    finally { setPending(false); }
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div><p className="mb-1 text-sm text-muted">账号与权限</p><h1 className="text-2xl font-semibold tracking-tight">用户与角色</h1><p className="mt-2 text-sm text-muted">管理账号状态、角色和已登录设备。角色或停用变更会使现有 Token 失效。</p></div>
        <Button isDisabled={loading} onPress={() => void reload()} variant="secondary"><RefreshCw size={16} />刷新</Button>
      </header>

      {(error || searchError) && <Feedback text={error ?? searchError ?? ""} />}

      <div className="max-w-xl space-y-2">
        <TextField className="max-w-xl flex-1" fullWidth>
          <Label>查找用户</Label><Input fullWidth onChange={(event) => setQuery(event.target.value)} placeholder="邮箱或显示名称" value={query} />
        </TextField>
        <p className={`text-xs ${queryIsValid ? "text-muted" : "text-warning"}`}>
          {!queryIsValid ? "请输入至少 2 个字符；清空可查看最近用户。" : loading ? "正在搜索…" : `已加载 ${users.length} 个用户，输入后会自动搜索。`}
        </p>
      </div>

      <section className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(360px,.8fr)]">
        <Card variant="default">
          <Card.Header><UserRoundCog className="text-muted" size={19} /><div><Card.Title>账号列表</Card.Title><Card.Description>每页 20 个，按账号游标继续加载</Card.Description></div></Card.Header>
          <Card.Content className="gap-0 pt-1">
            {loading ? <Empty text="正在读取用户…" /> : users.length === 0 ? <Empty text="没有匹配的用户" /> : users.map((user) => (
              <button className={`grid w-full gap-3 border-b border-separator px-2 py-4 text-left last:border-0 sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center ${selectedId === user.id ? "bg-default/50" : "hover:bg-default/30"}`} key={user.id} onClick={() => setSelectedId(user.id)} type="button">
                <div className="min-w-0"><p className="truncate text-sm font-medium">{user.displayName}</p><p className="truncate text-xs text-muted">{user.email} · #{user.id}</p></div>
                <div className="flex flex-wrap gap-1">{user.roles.map((role) => <Chip key={role} size="sm" variant="soft">{role}</Chip>)}</div>
                <Chip color={user.status === "ACTIVE" ? "success" : "default"} size="sm" variant="soft">{user.status === "ACTIVE" ? "启用" : "停用"}</Chip>
              </button>
            ))}
            {hasMore && <Button className="mt-4" fullWidth isDisabled={loadingMore} onPress={() => void loadMore()} variant="tertiary">{loadingMore ? "正在加载…" : "加载更多用户"}</Button>}
          </Card.Content>
        </Card>

        <div className="space-y-6">
          <Card variant="default">
            <Card.Header><ShieldCheck className="text-muted" size={19} /><div><Card.Title>权限与状态</Card.Title><Card.Description>{selected ? selected.email : "请选择用户"}</Card.Description></div></Card.Header>
            <Card.Content className="gap-4 pt-2">
              {!selected ? <Empty text="从左侧选择一个用户" /> : <>
                <div className="space-y-3">{roles.map((role) => (
                  <Checkbox isDisabled={pending || loading || role.code === "USER"} isSelected={selected.roles.includes(role.code)} key={role.code} onChange={(enabled) => void toggleRole(role.code, enabled)}>
                    <Checkbox.Content><Checkbox.Control><Checkbox.Indicator /></Checkbox.Control><div><Label>{role.name}</Label><p className="text-xs text-muted">{role.code}</p></div></Checkbox.Content>
                  </Checkbox>
                ))}</div>
                <Button isDisabled={pending || loading} onPress={() => void mutate(() => updateAdminUserStatus(selected.id, selected.status === "ACTIVE" ? "DISABLED" : "ACTIVE"))} variant="secondary">
                  {selected.status === "ACTIVE" ? "停用账号" : "重新启用"}
                </Button>
              </>}
            </Card.Content>
          </Card>

          <Card variant="default">
            <Card.Header><Card.Title>登录会话</Card.Title><Card.Description>{selected ? `${selected.activeSessions} 个有效会话` : "请选择用户"}</Card.Description></Card.Header>
            <Card.Content className="gap-0 pt-1">
              {sessionsLoading ? <Empty text="正在读取会话…" /> : sessions.length === 0 ? <Empty text="暂无会话记录" /> : sessions.map((session) => (
                <div className="border-b border-separator py-4 last:border-0" key={session.id}>
                  <div className="flex items-start justify-between gap-3"><div><p className="text-sm font-medium">{session.deviceName || session.clientType || "未知设备"}</p><p className="mt-1 text-xs text-muted">{session.ipAddress || "未记录 IP"} · {formatDate(session.lastSeenAt || session.createdAt)}</p></div><Chip color={session.status === "ACTIVE" ? "success" : "default"} size="sm" variant="soft">{session.status}</Chip></div>
                  {session.status === "ACTIVE" && <Button className="mt-3" isDisabled={pending || loading} onPress={() => void revoke(session.id)} size="sm" variant="tertiary">撤销会话</Button>}
                </div>
              ))}
            </Card.Content>
          </Card>
        </div>
      </section>
    </div>
  );
}

function Empty({ text }: { text: string }) { return <p className="py-10 text-center text-sm text-muted">{text}</p>; }
function Feedback({ text }: { text: string }) { return <div className="rounded-xl bg-danger-soft px-4 py-3 text-sm text-danger" role="alert">{text}</div>; }
function formatDate(value: string) { return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); }
function message(reason: unknown) { return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试"; }
