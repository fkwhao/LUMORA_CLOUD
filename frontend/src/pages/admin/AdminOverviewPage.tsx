import { Activity, ArrowUpRight, Boxes, CircleDollarSign, ServerCog, Users } from "lucide-react";

const services = [
  ["User Service", "登录与设备会话", "46101"],
  ["Billing Service", "套餐、额度与账本", "46102"],
  ["Model Catalog", "模型与价格版本", "46103"],
  ["Model Gateway", "流式代理与 Usage", "46104"],
];

export function AdminOverviewPage() {
  return (
    <div className="page-stack">
      <header className="page-intro">
        <div>
          <span className="eyebrow">OPERATIONS / LIVE SHAPE</span>
          <h1>运营总览</h1>
          <p>这是管理端信息架构骨架；权限最终由 `/api/admin/**` 在服务端强制校验。</p>
        </div>
        <span className="live-chip"><i /> 4 个领域服务待接入</span>
      </header>

      <section className="admin-metrics">
        <Metric icon={Users} label="注册用户" value="1,284" note="本月 +12.4%" />
        <Metric icon={CircleDollarSign} label="本月套餐收入" value="¥38.6K" note="演示数据" />
        <Metric icon={Activity} label="今日模型请求" value="6,842" note="失败率 0.03%" />
        <Metric icon={Boxes} label="已发布模型" value="12" note="3 个价格版本" />
      </section>

      <section className="admin-grid">
        <article className="panel service-map">
          <header className="panel-header"><div><span className="eyebrow">SERVICE BOUNDARIES</span><h2>后端服务地图</h2></div><ServerCog size={19} /></header>
          <div className="service-list">
            {services.map(([name, description, port]) => (
              <div key={name}>
                <span><i /></span>
                <p><strong>{name}</strong><small>{description}</small></p>
                <code>:{port}</code>
              </div>
            ))}
          </div>
        </article>

        <article className="panel admin-queue">
          <header className="panel-header"><div><span className="eyebrow">NEXT IMPLEMENTATION</span><h2>领域落地顺序</h2></div></header>
          <ol>
            <li><span>01</span><p><strong>User Service</strong><small>登录、刷新与角色</small></p></li>
            <li><span>02</span><p><strong>Billing Service</strong><small>套餐、周桶与状态机</small></p></li>
            <li><span>03</span><p><strong>Model Gateway</strong><small>预占、流式代理与结算</small></p></li>
          </ol>
          <a href="/console">查看用户侧结构 <ArrowUpRight size={15} /></a>
        </article>
      </section>
    </div>
  );
}

function Metric({ icon: Icon, label, value, note }: {
  icon: typeof Users;
  label: string;
  value: string;
  note: string;
}) {
  return (
    <article>
      <span><Icon size={17} /></span>
      <p>{label}</p>
      <strong>{value}</strong>
      <small>{note}</small>
    </article>
  );
}
