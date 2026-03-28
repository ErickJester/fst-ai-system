import React, { createContext, useContext, useState, useCallback } from 'react'

const AuthContext = createContext(null)

const MOCK_USERS = [
  { id: 1, email: 'investigador@ipn.mx', password: 'password', name: 'M. Sánchez', role: 'investigador', initials: 'MS' },
  { id: 2, email: 'admin@ipn.mx', password: 'password', name: 'A. Ramírez', role: 'admin', initials: 'AR' },
]

function loadUser() {
  try {
    const raw = localStorage.getItem('fst_user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(loadUser)

  const login = useCallback((email, password) => {
    const found = MOCK_USERS.find(
      (u) => u.email === email && u.password === password
    )
    if (!found) {
      return { ok: false, error: 'Credenciales incorrectas' }
    }
    const userData = { id: found.id, email: found.email, name: found.name, role: found.role, initials: found.initials }
    localStorage.setItem('fst_user', JSON.stringify(userData))
    localStorage.setItem('fst_token', `mock-token-${found.id}`)
    setUser(userData)
    return { ok: true }
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('fst_user')
    localStorage.removeItem('fst_token')
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
