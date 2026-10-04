import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

const publicUser = ({ id, nombre, apellidos, email, identificador, role, must_change_password }) =>
  ({ id, nombre, apellidos, email, identificador, role, must_change_password })

// POST /auth/login → { token, user }
export async function login(email, password) {
  if (USE_MOCKS) {
    const u = db.users.find((x) => x.email === email.trim().toLowerCase())
    if (!u || !u.is_active || password !== db.MOCK_PASSWORD) return fail(401, 'Correo o contraseña incorrectos.')
    return reply({ token: `mock-token-${u.id}`, user: publicUser(u) })
  }
  return (await api.post('/auth/login', { email, password })).data
}

// POST /auth/logout
export async function logout() {
  if (USE_MOCKS) return reply({ ok: true }, 0)
  return (await api.post('/auth/logout')).data
}

// POST /auth/forgot → misma respuesta exista o no la cuenta
export async function forgotPassword(email) {
  if (USE_MOCKS) return reply({ ok: true })
  return (await api.post('/auth/forgot', { email })).data
}

// POST /auth/change-password
export async function changePassword(actual, nueva) {
  if (USE_MOCKS) {
    if (actual !== db.MOCK_PASSWORD) return fail(400, 'La contraseña actual no es correcta.')
    return reply({ ok: true })
  }
  return (await api.post('/auth/change-password', { current_password: actual, new_password: nueva })).data
}

// GET /me
export async function getMe(userId) {
  if (USE_MOCKS) return reply(publicUser(db.users.find((u) => u.id === userId)))
  return (await api.get('/me')).data
}

// PATCH /me → { nombre, apellidos, email }
export async function updateMe(userId, changes) {
  if (USE_MOCKS) {
    const u = db.users.find((x) => x.id === userId)
    Object.assign(u, changes)
    return reply(publicUser(u))
  }
  return (await api.patch('/me', changes)).data
}
