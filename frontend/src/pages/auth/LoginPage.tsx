import { ArrowRight, KeyRound, ShieldCheck } from "lucide-react";
import type { FormEvent } from "react";

import { BrandMark } from "../../components/BrandMark";

export function LoginPage() {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    window.location.assign("/console");
  }

  return (
    <main className="login-page">
      <section className="login-story">
        <a className="login-brand" href="/console"><BrandMark /> Lumora</a>
        <div className="story-copy">
          <span className="eyebrow">MODEL ACCESS, CLEARLY ACCOUNTED.</span>
          <h1>把模型能力交给工作，<em>把边界留给自己。</em></h1>
          <p>网页控制台使用独立会话。登录后管理套餐与账单，不会读取 Desktop 的登录凭据。</p>
        </div>
        <div className="trust-line"><ShieldCheck size={16} /> Desktop 与网页会话相互隔离</div>
      </section>

      <section className="login-panel">
        <form onSubmit={submit}>
          <span className="login-icon"><KeyRound size={20} /></span>
          <p className="eyebrow">WELCOME BACK</p>
          <h2>登录 Lumora Cloud</h2>
          <p className="form-intro">查看套餐、购买额度与管理云端模型使用。</p>
          <label>
            <span>邮箱</span>
            <input type="email" defaultValue="demo@lumora.local" required />
          </label>
          <label>
            <span>密码</span>
            <input type="password" placeholder="请输入密码" required />
          </label>
          <button type="submit">进入控制台 <ArrowRight size={17} /></button>
          <small>当前为界面骨架，提交后进入演示控制台。</small>
        </form>
      </section>
    </main>
  );
}
