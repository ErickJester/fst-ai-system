import React, { createContext, useContext, useState, useCallback } from 'react'
import * as authService from '../services/auth'

const AuthContext = createContext(null)

function loadUser() {
  try {
    const raw = localStorage.getItem('fst_user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function saveSession(token, user) {
  try {
    if (user) {
      localStorage.setItem('fst_user', JSON.stringify(user))
      localStorage.setItem('fst_token', token)
    } else {
      localStorage.removeItem('fst_user')
      localStorage.removeItem('fst_token')
    }
  } catch {
    // sin almacenamiento: la sesión dura lo que la pestaña
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(loadUser)

  const login = useCallback(async (email, password) => {
    try {
      const { token, user: u } = await authService.login(email, password)
      saveSession(token, u)
      setUser(u)
      return { ok: true, user: u }
    } catch (err) {
      return { ok: false, error: err.response?.data?.error || 'No se pudo iniciar sesión.' }
    }
  }, [])

  const logout = useCallback(async () => {
    try { await authService.logout() } catch { /* se cierra igual en el cliente */ }
    saveSession(null, null)
    setUser(null)
  }, [])

  const updateUser = useCallback((u) => {
    saveSession(localStorage.getItem('fst_token'), u)
    setUser(u)
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
