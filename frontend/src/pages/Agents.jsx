import { useEffect, useState } from "react";
import api, { errMsg } from "../api";

export default function Agents() {
  const [agents, setAgents] = useState([]);
  const [f, setF] = useState({ name: "", email: "", password: "" });
  const [err, setErr] = useState("");
  const load = () => api.get("/api/admin/agents").then((r) => setAgents(r.data));
  useEffect(() => { load(); }, []);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const add = async (e) => {
    e.preventDefault(); setErr("");
    try { await api.post("/api/admin/agents", f); setF({ name: "", email: "", password: "" }); load(); } catch (ex) { setErr(errMsg(ex)); }
  };
  const toggle = (id) => api.patch(`/api/admin/users/${id}/toggle-active`).then(load);

  return (
    <>
      <h1>Agents</h1>
      <form className="card" onSubmit={add}>
        <h3 style={{ marginTop: 0 }}>Add agent</h3>
        <input placeholder="Name" value={f.name} onChange={set("name")} required />
        <input type="email" placeholder="Email" value={f.email} onChange={set("email")} required />
        <input type="password" placeholder="Temporary password (min 6)" value={f.password} onChange={set("password")} required />
        {err && <div className="error">{err}</div>}
        <button>Create agent</button>
      </form>
      <table>
        <thead><tr><th>Name</th><th>Email</th><th>Status</th><th></th></tr></thead>
        <tbody>
          {agents.map((a) => (
            <tr key={a.id}><td>{a.name}</td><td>{a.email}</td><td>{a.active ? "Active" : "Deactivated"}</td>
              <td><button className="secondary" onClick={() => toggle(a.id)}>{a.active ? "Deactivate" : "Activate"}</button></td></tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
