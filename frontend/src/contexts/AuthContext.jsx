import React, { createContext, useContext, useState, useCallback } from 'react'
import * as auth from '../services/auth'
import { TOKEN_KEY } from '../services/config'

// Sesión de la cuenta. Las cuentas y contraseñas las resuelve services/auth.js
// (datos de ejemplo mientras no haya backend). La sesión vive en sessionStorage:
// al cerrar el navegador se vuelve a pedir login.
const AuthContext = createContext(null)

const KEY = 'fst.user'

const session = {
  get() {
    try {
      localStorage.removeItem(KEY) // sesión que guardaba la versión anterior
      const v = sessionStorage.getItem(KEY)
      const u = v ? JSON.parse(v) : null
      return u?.id ? u : null // las sesiones sin id son de la versión sin servicios
    } catch {
      return null
    }
  },
  set(u, token) {
    try {
      if (u) sessionStorage.setItem(KEY, JSON.stringify(u))
      else sessionStorage.removeItem(KEY)
      if (token) sessionStorage.setItem(TOKEN_KEY, token)
      if (!u) sessionStorage.removeItem(TOKEN_KEY)
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
  const logout = useCallback(() => setUser(null), [setUser])

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
