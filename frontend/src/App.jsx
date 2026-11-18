import { Navigate, NavLink, Outlet, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth.jsx";
import Login from "./pages/Login.jsx";
import Chat from "./pages/Chat.jsx";
import Tickets from "./pages/Tickets.jsx";
import NewTicket from "./pages/NewTicket.jsx";
import TicketDetail from "./pages/TicketDetail.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import Knowledge from "./pages/Knowledge.jsx";
import Agents from "./pages/Agents.jsx";

const MENU = {
  CUSTOMER: [["/chat", "AI Chat"], ["/tickets", "My Tickets"], ["/tickets/new", "New Ticket"]],
  AGENT: [["/tickets", "Ticket Inbox"]],
  ADMIN: [["/dashboard", "Dashboard"], ["/tickets", "All Tickets"], ["/knowledge", "Knowledge Base"], ["/agents", "Agents"]],
};

function Layout() {
  const { user, logout } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return (
    <div className="layout">
      <aside className="sidebar">
        <h2>SupportAI</h2>
        {MENU[user.role].map(([to, label]) => (
          <NavLink key={to} to={to} end>{label}</NavLink>
        ))}
        <div className="spacer" />
        <div className="who">{user.name}<br /><small>{user.role}</small></div>
        <button className="ghost" onClick={logout}>Log out</button>
      </aside>
      <main className="content"><Outlet /></main>
    </div>
  );
}

function Home() {
  const { user } = useAuth();
  return <Navigate to={user.role === "CUSTOMER" ? "/chat" : user.role === "ADMIN" ? "/dashboard" : "/tickets"} replace />;
}

function Only({ roles, children }) {
  const { user } = useAuth();
  return roles.includes(user.role) ? children : <Navigate to="/" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/chat" element={<Only roles={["CUSTOMER"]}><Chat /></Only>} />
        <Route path="/tickets" element={<Tickets />} />
        <Route path="/tickets/new" element={<Only roles={["CUSTOMER"]}><NewTicket /></Only>} />
        <Route path="/tickets/:id" element={<TicketDetail />} />
        <Route path="/dashboard" element={<Only roles={["ADMIN"]}><Dashboard /></Only>} />
        <Route path="/knowledge" element={<Only roles={["ADMIN"]}><Knowledge /></Only>} />
        <Route path="/agents" element={<Only roles={["ADMIN"]}><Agents /></Only>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
