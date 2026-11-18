import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api, { errMsg } from "../api";
import { useAuth } from "../auth.jsx";

export const Badge = ({ v }) => <span className={`badge ${String(v).split(" ")[0]}`}>{v}</span>;

export default function Tickets() {
  const { user } = useAuth();
  const [rows, setRows] = useState([]);
  const [filter, setFilter] = useState("ALL");
  const [err, setErr] = useState("");
  useEffect(() => { api.get("/api/tickets").then((r) => setRows(r.data)).catch((e) => setErr(errMsg(e))); }, []);
  const shown = filter === "ALL" ? rows : rows.filter((t) => t.status === filter);

  return (
    <>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h1>{user.role === "CUSTOMER" ? "My Tickets" : "Tickets"}</h1>
        <select style={{ width: 180 }} value={filter} onChange={(e) => setFilter(e.target.value)}>
          {["ALL", "OPEN", "ASSIGNED", "IN_PROGRESS", "PENDING", "ESCALATED", "RESOLVED", "CLOSED"].map((s) => <option key={s}>{s}</option>)}
        </select>
      </div>
      {err && <div className="error">{err}</div>}
      <table>
        <thead><tr><th>#</th><th>Subject</th><th>Category</th><th>Priority</th><th>Sentiment</th><th>Status</th>{user.role !== "CUSTOMER" && <th>Agent</th>}</tr></thead>
        <tbody>
          {shown.map((t) => (
            <tr key={t.id}>
              <td>{t.id}</td>
              <td><Link to={`/tickets/${t.id}`}>{t.subject}</Link></td>
              <td>{t.category}</td>
              <td><Badge v={t.priority} /></td>
              <td><Badge v={t.sentiment} /></td>
              <td><Badge v={t.status} /></td>
              {user.role !== "CUSTOMER" && <td>{t.agent?.name || "—"}</td>}
            </tr>
          ))}
          {!shown.length && <tr><td colSpan="7" className="muted">No tickets yet.</td></tr>}
        </tbody>
      </table>
    </>
  );
}
