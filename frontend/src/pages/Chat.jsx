import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import api, { errMsg } from "../api";

export default function Chat() {
  const nav = useNavigate();
  const [convId, setConvId] = useState(null);
  const [msgs, setMsgs] = useState([{ sender: "AI", content: "Hi! Ask me anything about orders, payments, refunds or your account." }]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const end = useRef();
  useEffect(() => { end.current?.scrollIntoView({ behavior: "smooth" }); }, [msgs, busy]);

  const send = async (e) => {
    e.preventDefault();
    const q = text.trim();
    if (!q || busy) return;
    setText(""); setErr(""); setBusy(true);
    setMsgs((m) => [...m, { sender: "CUSTOMER", content: q }]);
    try {
      const { data } = await api.post("/api/chat/message", { conversationId: convId, message: q });
      setConvId(data.conversationId);
      setMsgs((m) => [...m, data.reply]);
    } catch (ex) { setErr(errMsg(ex)); }
    setBusy(false);
  };

  const handoff = async () => {
    try {
      const { data } = await api.post(`/api/chat/${convId}/handoff`);
      nav(`/tickets/${data.id}`);
    } catch (ex) { setErr(errMsg(ex)); }
  };

  return (
    <>
      <h1>AI Support Chat</h1>
      <div className="chatbox">
        {msgs.map((m, i) => (
          <div key={i} className={`msg ${m.sender === "CUSTOMER" ? "me" : "ai"}`}>
            {m.content}
            {m.sources && <small>Source: {m.sources}</small>}
          </div>
        ))}
        {busy && <div className="msg ai muted">Typing…</div>}
        <div ref={end} />
      </div>
      {err && <div className="error">{err}</div>}
      <form className="row" onSubmit={send}>
        <input placeholder="Type your question…" value={text} onChange={(e) => setText(e.target.value)} />
        <button disabled={busy}>Send</button>
        <button type="button" className="secondary" disabled={!convId} onClick={handoff}>Talk to a human</button>
      </form>
    </>
  );
}
