import React, { createContext, useContext, useState, useCallback } from 'react'
import { USERS } from '../data/mock'
import { store } from '../lib/fst'

// Sesión simulada: no hay backend. Las cuentas de USERS entran directo; cualquier
// otro correo @ipn.mx se trata como cuenta nueva con contraseña temporal (2i).
// La sesión vive en sessionStorage: al cerrar el navegador se vuelve a pedir login.
const AuthContext = createContext(null)

const KEY = 'fst.user'
const session = {
  get() {
    try {
      localStorage.removeItem(KEY) // sesión que guardaba la versión anterior
      const v = sessionStorage.getItem(KEY)
      return v ? JSON.parse(v) : null
    } catch {
      return null
    }
  },
  set(u) {
    try {
      if (u) sessionStorage.setItem(KEY, JSON.stringify(u))
      else sessionStorage.removeItem(KEY)
      localStorage.removeItem(KEY) // sesión que guardaba la versión anterior
    } catch {
      /* sin almacenamiento */
    }
  },
}

// Las cuentas modificadas (contraseña temporal ya cambiada, datos del perfil)
// se recuerdan en localStorage, como lo haría el servidor.
const cuentas = {
  get: (correo) => store.get('cuentas', {})[correo] || null,
  save(u) {
    const all = store.get('cuentas', {})
    all[u.correo] = u
    store.set('cuentas', all)
  },
}

function cuentaNueva(correo) {
  const local = correo.split('@')[0]
  return {
    ini: local.slice(0, 2).toUpperCase(),
    nombre: local,
    apellidos: '',
    correo,
    idInst: '',
    rol: 'Investigador',
    admin: false,
    temporal: true,
  }
}

export function AuthProvider({ children }) {
  const [user, setUserState] = useState(() => session.get())

  const setUser = useCallback((u) => {
    session.set(u)
    setUserState(u)
  }, [])

  const login = useCallback((correo) => {
    const c = correo.trim().toLowerCase()
    const u = cuentas.get(c) || USERS.find((x) => x.correo === c) || cuentaNueva(c)
    setUser(u)
    return u
  }, [setUser])

  const logout = useCallback(() => setUser(null), [setUser])

  const updateUser = useCallback((cambios) => {
    setUserState((prev) => {
      const u = { ...prev, ...cambios }
      session.set(u)
      cuentas.save(u)
      return u
    })
  }, [])

  return (
    <AuthContext.Provider value={{ user, login, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
