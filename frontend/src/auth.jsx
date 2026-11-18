import { createContext, useContext, useState } from "react";
import api from "./api";

const Ctx = createContext(null);
export const useAuth = () => useContext(Ctx);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => JSON.parse(localStorage.getItem("user") || "null"));

  const finish = (data) => {
    localStorage.setItem("token", data.token);
    localStorage.setItem("user", JSON.stringify(data.user));
    setUser(data.user);
    return data.user;
  };
  const login = async (email, password) => finish((await api.post("/api/auth/login", { email, password })).data);
  const register = async (name, email, password) => finish((await api.post("/api/auth/register", { name, email, password })).data);
  const logout = () => { localStorage.clear(); setUser(null); };

  return <Ctx.Provider value={{ user, login, register, logout }}>{children}</Ctx.Provider>;
}
