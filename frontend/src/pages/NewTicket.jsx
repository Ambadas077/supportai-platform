import { useState } from "react";
import { useNavigate } from "react-router-dom";
import api, { errMsg } from "../api";

export default function NewTicket() {
  const nav = useNavigate();
  const [subject, setSubject] = useState("");
  const [description, setDescription] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setErr("");
    try { const { data } = await api.post("/api/tickets", { subject, description }); nav(`/tickets/${data.id}`); }
    catch (ex) { setErr(errMsg(ex)); setBusy(false); }
  };

  return (
    <>
      <h1>Create a Ticket</h1>
      <form className="card" onSubmit={submit}>
        <input placeholder="Subject" value={subject} onChange={(e) => setSubject(e.target.value)} required />
        <textarea placeholder="Describe your issue" value={description} onChange={(e) => setDescription(e.target.value)} required />
        <p className="muted">Our AI will categorise your ticket, set its priority and route it to the right agent.</p>
        {err && <div className="error">{err}</div>}
        <button disabled={busy}>{busy ? "Submitting…" : "Submit ticket"}</button>
      </form>
    </>
  );
}
