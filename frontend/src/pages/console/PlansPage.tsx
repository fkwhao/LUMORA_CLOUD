import { ArrowLeft, Check, ShieldCheck } from "lucide-react";

import { plans } from "../../features/billing/demo-data";

export function PlansPage() {
  return (
    <div className="page-stack plans-page">
      <header className="page-intro">
        <div>
          <a className="back-link" href="/console"><ArrowLeft size={15} /> 返回概览</a>
          <span className="eyebrow">CHOOSE YOUR CAPACITY</span>
          <h1>选择套餐</h1>
          <p>套餐按月生效，并在有效期内按周生成独立额度周期。</p>
        </div>
        <div className="secure-note"><ShieldCheck size={16} /> 购买操作仅在网页端完成</div>
      </header>

      <section className="plan-grid">
        {plans.map((plan) => (
          <article className={plan.featured ? "plan-card featured" : "plan-card"} key={plan.name}>
            {plan.featured && <span className="plan-ribbon">RECOMMENDED</span>}
            <span className="plan-index">0{plans.indexOf(plan) + 1}</span>
            <h2>{plan.name}</h2>
            <p>{plan.description}</p>
            <strong>{plan.price}<small>/月</small></strong>
            <ul>
              <li><Check size={15} /> {plan.quota}</li>
              <li><Check size={15} /> 云端模型目录</li>
              <li><Check size={15} /> 每周额度自动刷新</li>
            </ul>
            <button type="button">选择 {plan.name}</button>
          </article>
        ))}
      </section>
      <p className="prototype-note">当前为购买页面骨架，支付、续费和订单状态尚未接入 Billing Service。</p>
    </div>
  );
}
