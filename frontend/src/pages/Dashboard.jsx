import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import api, { errMsg } from "../api";

const COLORS = ["#4f46e5", "#06b6d4", "#f59e0b", "#ef4444", "#10b981", "#8b5cf6", "#64748b"];
const toArr = (o = {}) => Object.entries(o).map(([name, value]) => ({ name, value }));

function Stat({ label, value }) { return <div className="card stat"><b>{value}</b><span>{label}</span></div>; }

function Pie1({ title, data }) {
  return (
    <div className="card">
      <h3 style={{ marginTop: 0 }}>{title}</h3>
      <ResponsiveContainer width="100%" height={220}>
        <PieChart>
          <Pie data={data} dataKey="value" nameKey="name" outerRadius={80} label>
            {data.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
          </Pie>
          <Tooltip />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

export default function Dashboard() {
  const [d, setD] = useState(null);
  const [err, setErr] = useState("");
  useEffect(() => { api.get("/api/admin/dashboard").then((r) => setD(r.data)).catch((e) => setErr(errMsg(e))); }, []);
  if (err) return <div className="error">{err}</div>;
  if (!d) return <p>Loading…</p>;

  return (
    <>
      <h1>Dashboard</h1>
      <div className="grid">
        <Stat label="Total tickets" value={d.totalTickets} />
        <Stat label="Open tickets" value={d.openTickets} />
        <Stat label="Resolved" value={d.resolvedTickets} />
        <Stat label="AI resolution rate" value={`${d.aiResolutionRate}%`} />
        <Stat label="Escalation rate" value={`${d.escalationRate}%`} />
      </div>
      <div className="grid" style={{ gridTemplateColumns: "repeat(auto-fit,minmax(320px,1fr))" }}>
        <Pie1 title="By category" data={toArr(d.byCategory)} />
        <Pie1 title="By priority" data={toArr(d.byPriority)} />
        <Pie1 title="Customer sentiment" data={toArr(d.bySentiment)} />
        <Pie1 title="By status" data={toArr(d.byStatus)} />
      </div>
      <div className="card">
        <h3 style={{ marginTop: 0 }}>Agent performance</h3>
        <ResponsiveContainer width="100%" height={260}>
          <BarChart data={d.agents}>
            <CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="name" /><YAxis allowDecimals={false} /><Tooltip />
            <Bar dataKey="assigned" fill="#4f46e5" /><Bar dataKey="resolved" fill="#10b981" />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </>
  );
}
