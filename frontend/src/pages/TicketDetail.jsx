import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import api, { errMsg } from "../api";
import { useAuth } from "../auth.jsx";
import { Badge } from "./Tickets.jsx";

const STATUSES = ["OPEN", "ASSIGNED", "IN_PROGRESS", "PENDING", "ESCALATED", "RESOLVED", "CLOSED"];

export default function TicketDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const staff = user.role !== "CUSTOMER";
  const [data, setData] = useState(null);
  const [agents, setAgents] = useState([]);
  const [text, setText] = useState("");
  const [internal, setInternal] = useState(false);
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => api.get(`/api/tickets/${id}`).then((r) => setData(r.data)).catch((e) => setErr(errMsg(e))), [id]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => { if (user.role === "ADMIN") api.get("/api/admin/agents").then((r) => setAgents(r.data)); }, [user.role]);

  const run = async (fn) => { setErr(""); try { await fn(); await load(); } catch (e) { setErr(errMsg(e)); } };
  const addComment = (e) => { e.preventDefault(); run(async () => { await api.post(`/api/tickets/${id}/comments`, { text, internal }); setText(""); setInternal(false); }); };
  const suggest = async () => { setBusy(true); try { const r = await api.post(`/api/tickets/${id}/suggest-reply`); setText(r.data.reply); } catch (e) { setErr(errMsg(e)); } setBusy(false); };

  if (!data) return err ? <div className="error">{err}</div> : <p>Loading…</p>;
  const t = data.ticket;
  const canReopen = !staff && (t.status === "RESOLVED" || t.status === "CLOSED");

  return (
    <>
      <h1>#{t.id} {t.subject}</h1>
      <div className="card">
        <div className="row" style={{ flexWrap: "wrap" }}>
          <Badge v={t.status} /> <Badge v={t.priority} /> <Badge v={t.sentiment} />
          <span className="badge">{t.category}</span>
          {t.confidence != null && <span className="muted">AI confidence {Math.round(t.confidence * 100)}%</span>}
        </div>
        <p style={{ whiteSpace: "pre-wrap" }}>{t.description}</p>
        <p className="muted">Customer: {t.customer?.name} · Agent: {t.agent?.name || "unassigned"}</p>
        {staff && (
          <div className="row">
            <select value={t.status} onChange={(e) => run(() => api.patch(`/api/tickets/${id}/status`, { status: e.target.value }))}>
              {STATUSES.map((s) => <option key={s}>{s}</option>)}
            </select>
            {user.role === "ADMIN" && (
              <select value={t.agent?.id || ""} onChange={(e) => run(() => api.patch(`/api/tickets/${id}/assign`, { agentId: Number(e.target.value) }))}>
                <option value="" disabled>Assign agent…</option>
                {agents.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            )}
          </div>
        )}
        {canReopen && <button onClick={() => run(() => api.patch(`/api/tickets/${id}/status`, { status: "OPEN" }))}>Re-open ticket</button>}
      </div>

      <div className="card">
        <h3 style={{ marginTop: 0 }}>Conversation</h3>
        {!data.comments.length && <p className="muted">No replies yet.</p>}
        {data.comments.map((c) => (
          <div key={c.id} className={`comment ${c.internal ? "internal" : ""}`}>
            <b>{c.author}</b> <small className="muted">{c.role}{c.internal ? " · internal note" : ""} · {new Date(c.createdAt).toLocaleString()}</small>
            <div style={{ whiteSpace: "pre-wrap" }}>{c.text}</div>
          </div>
        ))}
        <form onSubmit={addComment}>
          <textarea placeholder="Write a reply…" value={text} onChange={(e) => setText(e.target.value)} required />
          {err && <div className="error">{err}</div>}
          <div className="row">
            <button>Send</button>
            {staff && <button type="button" className="secondary" onClick={suggest} disabled={busy}>{busy ? "Thinking…" : "AI suggest reply"}</button>}
            {staff && <label><input type="checkbox" style={{ width: "auto" }} checked={internal} onChange={(e) => setInternal(e.target.checked)} /> Internal note</label>}
          </div>
          {staff && <small className="muted">AI drafts are never sent automatically. Review and edit before sending.</small>}
        </form>
      </div>
    </>
  );
}
