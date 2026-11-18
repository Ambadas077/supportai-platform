import { useEffect, useState } from "react";
import api, { errMsg } from "../api";

export default function Knowledge() {
  const [docs, setDocs] = useState([]);
  const [msg, setMsg] = useState({ ok: "", err: "" });
  const [busy, setBusy] = useState(false);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const load = () => api.get("/api/knowledge/documents").then((r) => setDocs(r.data));
  useEffect(() => { load(); }, []);

  const wrap = async (fn, ok) => {
    setBusy(true); setMsg({ ok: "", err: "" });
    try { await fn(); await load(); setMsg({ ok, err: "" }); } catch (e) { setMsg({ ok: "", err: errMsg(e) }); }
    setBusy(false);
  };
  const upload = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const fd = new FormData(); fd.append("file", file);
    wrap(() => api.post("/api/knowledge/upload", fd), `Indexed ${file.name}`);
    e.target.value = "";
  };
  const addArticle = (e) => {
    e.preventDefault();
    wrap(async () => { await api.post("/api/knowledge/articles", { title, content }); setTitle(""); setContent(""); }, "Article indexed");
  };

  return (
    <>
      <h1>Knowledge Base</h1>
      <div className="card">
        <h3 style={{ marginTop: 0 }}>Upload document (PDF, DOCX, TXT, MD, CSV)</h3>
        <input type="file" accept=".pdf,.docx,.txt,.md,.csv" onChange={upload} disabled={busy} />
        <p className="muted">The chatbot answers only from these documents. If nothing matches, it escalates to a human.</p>
      </div>
      <form className="card" onSubmit={addArticle}>
        <h3 style={{ marginTop: 0 }}>Add article</h3>
        <input placeholder="Title (e.g. Refund Policy)" value={title} onChange={(e) => setTitle(e.target.value)} required />
        <textarea placeholder="Article content" value={content} onChange={(e) => setContent(e.target.value)} required />
        <button disabled={busy}>{busy ? "Indexing…" : "Save & index"}</button>
      </form>
      {msg.ok && <div className="ok">{msg.ok}</div>}
      {msg.err && <div className="error">{msg.err}</div>}
      <table>
        <thead><tr><th>Document</th><th>Chunks</th><th>Added</th><th></th></tr></thead>
        <tbody>
          {docs.map((d) => (
            <tr key={d.id}><td>{d.fileName}</td><td>{d.chunks}</td><td>{new Date(d.createdAt).toLocaleString()}</td>
              <td><button className="danger" onClick={() => wrap(() => api.delete(`/api/knowledge/documents/${d.id}`), "Deleted")}>Delete</button></td></tr>
          ))}
          {!docs.length && <tr><td colSpan="4" className="muted">No documents yet.</td></tr>}
        </tbody>
      </table>
    </>
  );
}
