import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

// Los datos de ejemplo guardan la contraseña; la API nunca la devuelve.
const sinPassword = ({ password, ...u }) => u

// Contraseña temporal de 12 caracteres, sin los que se confunden (0/O, 1/l/I).
function temporal() {
  const abc = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
  return [...crypto.getRandomValues(new Uint32Array(12))].map((n) => abc[n % abc.length]).join('')
}

// GET /admin/users
export async function listUsers() {
  if (USE_MOCKS) return reply(db.users.map(sinPassword))
  return (await api.get('/admin/users')).data
}

// POST /admin/users → { nombre, apellidos, email, identificador }; se crea como Investigador.
// El 201 trae la contraseña temporal (password_temporal) una sola vez: el servidor solo
// guarda su hash y no la vuelve a dar. El correo, cuando exista, será un canal adicional.
export async function createUser(data) {
  if (USE_MOCKS) {
    const email = data.email.toLowerCase()
    if (db.users.some((u) => u.email === email)) return fail(409, 'Ya existe una cuenta con ese correo.')
    const password = temporal()
    const u = { id: Date.now(), role: 'INVESTIGADOR', is_active: true, must_change_password: true, ...data, email, password }
    db.users.push(u)
    return reply({ ...sinPassword(u), password_temporal: password })
  }
  return (await api.post('/admin/users', data)).data
}

// PATCH /admin/users/:id → { nombre, apellidos, email, identificador, role } (9.6, editar cuenta).
// 409 si el correo ya existe o si deja el sistema sin administradores activos.
export async function updateUser(id, cambios) {
  if (USE_MOCKS) {
    const u = db.users.find((x) => x.id === id)
    if (!u) return fail(404, 'No existe esa cuenta.')
    const email = cambios.email.toLowerCase()
    if (db.users.some((x) => x.email === email && x.id !== id)) return fail(409, 'Ya existe una cuenta con ese correo.')
    const admins = db.users.filter((x) => x.role === 'ADMIN' && x.is_active)
    if (u.role === 'ADMIN' && cambios.role !== 'ADMIN' && u.is_active && admins.length <= 1) return fail(409, 'Debe existir al menos un Administrador activo.')
    Object.assign(u, cambios, { email })
    return reply(sinPassword(u))
  }
  return (await api.patch(`/admin/users/${id}`, cambios)).data
}

// POST /admin/users/:id/reset-temporal → { password_temporal }. Propuesta: el documento no la
// define todavía. Genera otra temporal (se muestra una sola vez), invalida la contraseña
// anterior y la cuenta vuelve a pedir el cambio en su próximo acceso.
export async function resetTemporal(id) {
  if (USE_MOCKS) {
    const u = db.users.find((x) => x.id === id)
    if (!u) return fail(404, 'No existe esa cuenta.')
    const password = temporal()
    Object.assign(u, { password, must_change_password: true })
    return reply({ password_temporal: password })
  }
  return (await api.post(`/admin/users/${id}/reset-temporal`)).data
}

// PATCH /admin/users/:id → { is_active }; el servidor impide dejar el sistema sin administradores
export async function setUserActive(id, isActive) {
  if (USE_MOCKS) {
    const u = db.users.find((x) => x.id === id)
    if (!u) return fail(404, 'No existe esa cuenta.')
    const admins = db.users.filter((x) => x.role === 'ADMIN' && x.is_active)
    if (!isActive && u.role === 'ADMIN' && admins.length <= 1) return fail(409, 'Debe existir al menos un Administrador activo.')
    u.is_active = isActive
    return reply(sinPassword(u))
  }
  return (await api.patch(`/admin/users/${id}`, { is_active: isActive })).data
}

// GET /admin/system → disco, conductas y modelo en uso
export async function getSystem() {
  if (USE_MOCKS) return reply(db.system)
  return (await api.get('/admin/system')).data
}
