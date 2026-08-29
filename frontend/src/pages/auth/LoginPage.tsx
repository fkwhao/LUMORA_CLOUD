import { Button, Card, Chip, Input, Label, TextField } from "@heroui/react";
import { ArrowRight, ShieldCheck } from "lucide-react";
import { useState, type FormEvent } from "react";

import { ApiClientError, login, type UserProfile } from "../../api/auth";
import { BrandMark } from "../../components/BrandMark";

interface LoginPageProps {
  onAuthenticated: (user: UserProfile) => void;
}

export function LoginPage({ onAuthenticated }: LoginPageProps) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);

    const form = new FormData(event.currentTarget);
    try {
      const user = await login(String(form.get("email") ?? ""), String(form.get("password") ?? ""));
      onAuthenticated(user);
    } catch (reason) {
      setError(reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试");
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="flex min-h-screen flex-col bg-background px-4 py-6 text-foreground">
      <a className="mx-auto flex w-full max-w-6xl items-center gap-3" href="/">
        <BrandMark />
        <span className="text-sm font-semibold">Lumora Cloud</span>
      </a>

      <section className="flex flex-1 items-center justify-center py-10">
        <Card className="w-full max-w-md" variant="default">
          <Card.Header className="gap-3 px-2 pt-2">
            <Chip color="accent" size="sm" variant="soft">Cloud Console</Chip>
            <div>
              <Card.Title className="text-xl">登录 Lumora Cloud</Card.Title>
              <Card.Description className="mt-1">
                登录后查看套餐、用量并管理云端模型服务。
              </Card.Description>
            </div>
          </Card.Header>

          <Card.Content className="px-2 py-3">
            <form className="space-y-4" onSubmit={submit}>
              <TextField fullWidth isRequired name="email" type="email">
                <Label>邮箱</Label>
                <Input fullWidth autoComplete="email" placeholder="name@example.com" />
              </TextField>
              <TextField fullWidth isRequired name="password" type="password">
                <Label>密码</Label>
                <Input fullWidth autoComplete="current-password" placeholder="请输入密码" />
              </TextField>
              {error && (
                <p className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger" role="alert">
                  {error}
                </p>
              )}
              <Button fullWidth isDisabled={pending} type="submit" variant="primary">
                {pending ? "正在登录…" : "进入控制台"} {!pending && <ArrowRight size={17} />}
              </Button>
            </form>
          </Card.Content>

          <Card.Footer className="flex-col items-start gap-2 px-2 pb-2 text-xs text-muted">
            <span className="flex items-center gap-2"><ShieldCheck size={15} /> 网页与 Desktop 使用独立登录会话</span>
            <span>访问令牌仅保存在当前页面内存中，刷新令牌由安全 Cookie 保存。</span>
          </Card.Footer>
        </Card>
      </section>
    </main>
  );
}
