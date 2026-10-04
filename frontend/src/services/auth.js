import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

const publicUser = ({ id, nombre, apellidos, email, identificador, role, must_change_password }) =>
  ({ id, nombre, apellidos, email, identificador, role, must_change_password })

// RESET_LINK_MINUTES del documento: cuánto vale el enlace de recuperación.
const MINUTOS_ENLACE = 60

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
    if (!u.is_active || !password || (u.password && password !== u.password)) return fail(401, 'Correo o contraseña incorrectos.')
    return reply({ token: `mock-token-${u.id}`, user: publicUser(u) })
  }
  return (await api.post('/auth/login', { email, password })).data
}

// POST /auth/forgot → misma respuesta exista o no la cuenta; el enlace llega por correo
export async function forgotPassword(email) {
  if (USE_MOCKS) {
    const u = db.users.find((x) => x.email === email.trim().toLowerCase() && x.is_active)
    if (!u) return reply({ ok: true })
    const token = crypto.getRandomValues(new Uint32Array(4)).join('').slice(0, 24)
    db.resetTokens[token] = { userId: u.id, vence: Date.now() + MINUTOS_ENLACE * 60000, usado: false }
    // Sin correo, el enlace vuelve en la respuesta para poder abrirlo desde la pantalla.
    return reply({ ok: true, demo_enlace: '/restablecer?token=' + token })
  }
  return (await api.post('/auth/forgot', { email })).data
}

// POST /auth/reset → { token, new_password }; 400 si el enlace venció o ya se usó
export async function resetPassword(token, nueva) {
  if (USE_MOCKS) {
    const t = db.resetTokens[token]
    if (!t || t.usado || Date.now() > t.vence) return fail(400, 'El enlace venció o ya se usó.')
    t.usado = true
    Object.assign(db.users.find((x) => x.id === t.userId), { password: nueva, must_change_password: false })
    return reply({ ok: true })
  }
  return (await api.post('/auth/reset', { token, new_password: nueva })).data
}

// POST /auth/change-password → también sirve para dejar la contraseña temporal (2i)
export async function changePassword(userId, actual, nueva) {
  if (USE_MOCKS) {
    const u = db.users.find((x) => x.id === userId)
    if (!u) return fail(401, 'La sesión ya no es válida.')
    if (u.password && actual !== u.password) return fail(400, 'La contraseña actual no es correcta.')
    Object.assign(u, { password: nueva, must_change_password: false })
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
