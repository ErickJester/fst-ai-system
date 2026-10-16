import React, { createContext, useContext, useState, useCallback } from 'react'
import * as auth from '../services/auth'
import { TOKEN_KEY } from '../services/config'

// Sesión de la cuenta. Las cuentas y contraseñas las resuelve services/auth.js
// (datos de ejemplo mientras no haya backend). La sesión vive en localStorage: sobrevive a
// cerrar la pestaña, como pide el documento (token de larga duración, JWT_EXPIRES_HOURS = 168,
// §11.1). «Cerrar sesión» la borra; si el token vence, el servidor responde 401.
const AuthContext = createContext(null)

const KEY = 'fst.user'

const session = {
  get() {
    try {
      sessionStorage.removeItem(KEY) // sesión que guardaba la versión anterior
      sessionStorage.removeItem(TOKEN_KEY)
      const v = localStorage.getItem(KEY)
      const u = v ? JSON.parse(v) : null
      return typeof u?.id === 'string' ? u : null // id = idInstitucional; las demás son de versiones anteriores
    } catch {
      return null
    }
  },
  set(u, token) {
    try {
      if (u) localStorage.setItem(KEY, JSON.stringify(u))
      else localStorage.removeItem(KEY)
      if (token) localStorage.setItem(TOKEN_KEY, token)
      if (!u) localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* sin almacenamiento */
    }
  },
}

const ROL = { INVESTIGADOR: 'Investigador', ADMIN: 'Administrador' }

// Usuario de la API → usuario de la sesión, con la forma que usan las pantallas.
function aSesion(u) {
  const ini = u.apellidos ? u.nombre[0] + u.apellidos[0] : u.nombre.slice(0, 2)
  return {
    id: u.id,
    ini: ini.toUpperCase(),
    nombre: u.nombre,
    apellidos: u.apellidos,
    correo: u.email,
    idInst: u.identificador,
    rol: ROL[u.role],
    admin: u.role === 'ADMIN',
    temporal: u.must_change_password,
  }
}

export function AuthProvider({ children }) {
  const [user, setUserState] = useState(() => session.get())

  const setUser = useCallback((u, token) => {
    session.set(u, token)
    setUserState(u)
  }, [])

  const login = useCallback(async (correo, password) => {
    const r = await auth.login(correo, password)
    const u = aSesion(r.user)
    setUser(u, r.token)
    return u
  }, [setUser])

  // Cerrar sesión es solo del frontend: el servidor no guarda sesiones.
  // Cerrar sesión también borra los borradores de la revisión segundo a segundo.
  const logout = useCallback(() => {
    try {
      Object.keys(localStorage).filter((k) => k.startsWith('fst.revision.')).forEach((k) => localStorage.removeItem(k))
    } catch { /* sin almacenamiento */ }
    setUser(null)
  }, [setUser])

  // Cambia la contraseña (también la temporal del primer acceso).
  const cambiarPassword = useCallback(async (actual, nueva) => {
    setUser(aSesion(await auth.changePassword(user.id, actual, nueva)))
  }, [user, setUser])

  const actualizarPerfil = useCallback(async ({ nombre, apellidos, correo }) => {
    setUser(aSesion(await auth.updateMe(user.id, { nombre, apellidos, email: correo })))
  }, [user, setUser])

  return (
    <AuthContext.Provider value={{ user, login, logout, cambiarPassword, actualizarPerfil }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
