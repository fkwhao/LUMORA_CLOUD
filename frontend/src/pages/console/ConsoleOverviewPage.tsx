import { ArrowUpRight, Check, Cloud, KeyRound, RotateCw, Sparkles } from "lucide-react";

import { weeklyUsage } from "../../features/billing/demo-data";

export function ConsoleOverviewPage() {
  return (
    <div className="page-stack">
      <header className="page-intro">
        <div>
          <span className="eyebrow">WEDNESDAY · CURRENT CYCLE</span>
          <h1>套餐概览</h1>
          <p>查看当前权益与模型消耗。购买和续费只在这个网页控制台完成。</p>
        </div>
        <a className="primary-link" href="/console/plans">管理套餐 <ArrowUpRight size={16} /></a>
      </header>

      <section className="quota-hero">
        <div className="quota-copy">
          <div className="plan-line"><span>PRO PLAN</span><i>有效</i></div>
          <strong>2,184,320</strong>
          <p>本周剩余 Token</p>
          <div className="quota-meta">
            <span><b>815,680</b> 已使用</span>
            <span><b>3,000,000</b> 周额度</span>
            <span><RotateCw size={14} /> 4 天后刷新</span>
          </div>
        </div>
        <div className="quota-orbit" aria-label="额度剩余 72.8%">
          <div><strong>72.8%</strong><span>可用</span></div>
        </div>
      </section>

      <section className="console-grid">
        <article className="panel usage-panel">
          <header className="panel-header">
            <div><span className="eyebrow">LAST 7 DAYS</span><h2>每日消耗</h2></div>
            <small>共 815.7K Token</small>
          </header>
          <div className="usage-bars">
            {weeklyUsage.map((value, index) => (
              <div key={value + index}>
                <span style={{ height: `${value}%` }} />
                <small>{["四", "五", "六", "日", "一", "二", "三"][index]}</small>
              </div>
            ))}
          </div>
        </article>

        <article className="panel source-panel">
          <header className="panel-header"><div><span className="eyebrow">MODEL SOURCE</span><h2>模型来源</h2></div></header>
          <div className="source-option selected">
            <span><Cloud size={18} /></span>
            <div><strong>Lumora 套餐</strong><small>Reasoning Pro · 当前来源</small></div>
            <Check size={16} />
          </div>
          <div className="source-option">
            <span><KeyRound size={18} /></span>
            <div><strong>自定义供应商</strong><small>已保存 2 个 BYOK 连接</small></div>
          </div>
          <p>模型来源在 Desktop 设置中切换，网页端不会修改本地 API Key。</p>
        </article>
      </section>

      <section className="model-strip">
        <div><span className="model-glyph"><Sparkles size={18} /></span><p><small>当前云端模型</small><strong>Reasoning Pro</strong></p></div>
        <div><small>上下文</small><strong>200K</strong></div>
        <div><small>本周请求</small><strong>184</strong></div>
        <div><small>状态</small><strong className="healthy">可调用</strong></div>
      </section>
    </div>
  );
}
