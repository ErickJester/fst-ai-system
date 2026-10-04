import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

const publicUser = ({ id, nombre, apellidos, email, identificador, role, must_change_password }) =>
  ({ id, nombre, apellidos, email, identificador, role, must_change_password })

// Sin backend, un correo @ipn.mx desconocido entra como cuenta nueva con
// contraseña temporal, para poder recorrer el primer acceso (2i).
function cuentaNueva(email) {
  const u = { id: Date.now(), nombre: email.split('@')[0], apellidos: '', email, identificador: '', role: 'INVESTIGADOR', is_active: true, must_change_password: true }
  db.users.push(u)
  return u
}

// POST /auth/login → { token, user }
export async function login(email, password) {
  if (USE_MOCKS) {
    const e = email.trim().toLowerCase()
    const u = db.users.find((x) => x.email === e) || cuentaNueva(e)
    if (!u.is_active || !password) return fail(401, 'Correo o contraseña incorrectos.')
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

// POST /auth/change-password → también sirve para dejar la contraseña temporal (2i)
export async function changePassword(userId, actual, nueva) {
  if (USE_MOCKS) {
    const u = db.users.find((x) => x.id === userId)
    if (!u) return fail(401, 'La sesión ya no es válida.')
    u.must_change_password = false
    return reply(publicUser(u))
  }
  return (await api.post('/auth/change-password', { current_password: actual, new_password: nueva })).data
}

// PATCH /me → { nombre, apellidos, email }
export async function updateMe(userId, changes) {
  if (USE_MOCKS) {
    const u = db.users.find((x) => x.id === userId)
    if (!u) return fail(401, 'La sesión ya no es válida.')
    const email = changes.email.toLowerCase()
    if (db.users.some((x) => x.email === email && x.id !== userId)) return fail(409, 'Ya existe una cuenta con ese correo.')
    Object.assign(u, changes, { email })
    return reply(publicUser(u))
  }
  return (await api.patch('/me', changes)).data
}
