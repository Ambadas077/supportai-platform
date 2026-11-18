import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth.jsx";
import { errMsg } from "../api";

export default function Login() {
  const { user, login, register } = useAuth();
  const nav = useNavigate();
  const [mode, setMode] = useState("login");
  const [f, setF] = useState({ name: "", email: "", password: "" });
  const [err, setErr] = useState("");
  if (user) return <Navigate to="/" replace />;

  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const submit = async (e) => {
    e.preventDefault();
    setErr("");
    try {
      if (mode === "login") await login(f.email, f.password);
      else await register(f.name, f.email, f.password);
      nav("/");
    } catch (ex) { setErr(errMsg(ex)); }
  };

  return (
    <div className="card auth">
      <h1>SupportAI</h1>
      <p className="muted">{mode === "login" ? "Sign in to continue" : "Create a customer account"}</p>
      <form onSubmit={submit}>
        {mode === "register" && <input placeholder="Full name" value={f.name} onChange={set("name")} required />}
        <input type="email" placeholder="Email" value={f.email} onChange={set("email")} required />
        <input type="password" placeholder="Password (min 6 chars)" value={f.password} onChange={set("password")} required />
        {err && <div className="error">{err}</div>}
        <button style={{ width: "100%" }}>{mode === "login" ? "Log in" : "Register"}</button>
      </form>
      <p>
        <a href="#" onClick={(e) => { e.preventDefault(); setErr(""); setMode(mode === "login" ? "register" : "login"); }}>
          {mode === "login" ? "New here? Register" : "Have an account? Log in"}
        </a>
      </p>
      <small className="muted">Demo: customer@supportai.com / Customer@123<br />agent@supportai.com / Agent@123<br />admin@supportai.com / Admin@123</small>
    </div>
  );
}
